import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Compression
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
    jacoco
    id("com.gradleup.shadow") version "9.6.1"
    id("org.graalvm.buildtools.native") version "1.1.14"
}

group = "io.github.j0s3f"
version = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT").get()

// The facade this client exercises. It is published to JitPack, where a tag is available before
// Maven Central catches up; override with -PkotlogrammeVersion=... to test a different tag.
val kotlogrammeVersion = providers.gradleProperty("kotlogrammeVersion").orElse("v0.9.11").get()

// This application is not a library anyone links against, so it targets the newest LTS JVM and the
// newest stable Kotlin rather than the conservative versions the facade is bound to.
kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("com.github.J0s3f:kotlogram:$kotlogrammeVersion")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.jline:jline:4.4.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.slf4j:slf4j-simple:2.0.20")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Mordant ships JNA, FFM and GraalVM terminal backends. JNA references java.awt, which drags the JDK
// AWT libraries into the native image, and the FFM and GraalVM backends already cover every platform.
configurations.configureEach {
    exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-jna")
    exclude(group = "net.java.dev.jna")
}

application {
    applicationName = "kotlogramme"
    mainClass.set("org.kotlogramme.cli.MainKt")
    // JLine's FFM terminal provider, the Windows console fix and the facade's System.load of its native
    // library are all restricted operations; JDK 24+ warns unless native access is enabled, and a future
    // release blocks them outright.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        // Windows defaults stdout/stderr to the ANSI code page, which turns every non-ASCII
        // character into `?`; the client is UTF-8 end to end.
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

// Coverage is measured on demand with `jacocoTestReport`; the default `test` task stays as fast as before.
jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.test {
    useJUnitPlatform()
    // The terminal checks load JLine's FFM provider, which makes restricted calls; the application is
    // launched with this flag too, and without it every test run prints a warning.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// `--version` must report the version this build was produced under, including a release built
// with `-Pversion=0.1.0`, so bake the project version into a resource the application reads at
// startup. Without the property the value stays the development default.
val generatedVersionDir = layout.buildDirectory.dir("generated/version")
val generateVersionResource = tasks.register("generateVersionResource") {
    val projectVersion = version.toString()
    inputs.property("version", projectVersion)
    outputs.dir(generatedVersionDir)
    doLast {
        generatedVersionDir.get().file("kotlogramme-version.properties").asFile.apply {
            parentFile.mkdirs()
            writeText("version=$projectVersion\n")
        }
    }
}

sourceSets {
    main {
        resources.srcDir(generateVersionResource)
    }
}

// A single runnable jar, `build/libs/kotlogramme-all.jar`, that carries every runtime dependency.
// The facade loads its native library with `ClassLoader.getResourceAsStream`, so the bundled
// `native/<platform>/...` entries must survive shading at their original paths. Merging
// `META-INF/services` keeps ServiceLoader working for JLine and kotlinx.serialization. Nothing is
// relocated: the facade, the CLI and kotlinx.serialization are all found by their real names.
tasks.shadowJar {
    archiveFileName.set("kotlogramme-all.jar")
    // Service files are duplicated across dependencies; they must reach the transformer so their
    // contents are merged. Every other duplicate keeps the first entry, as usual.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesNotMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

// The fat jar is the default build output; `installDist`/`distZip` keep producing the plain
// distribution from the `jar` task and the runtime classpath, which still works.
tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

// The facade resolves generic protocol serializers reflectively. Deriving the registrations from
// the resolved jar keeps them complete when -PkotlogrammeVersion changes, without tracing Telegram.
val nativeOs = when {
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "macos"
    else -> "linux"
}
val nativeArchitecture = when (val architecture = System.getProperty("os.arch")) {
    "amd64", "x86_64" -> "x86_64"
    "aarch64", "arm64" -> "aarch64"
    else -> architecture
}
val nativePlatform = "$nativeOs-$nativeArchitecture"
val nativeExecutableName = if (nativeOs == "windows") "kotlogramme.exe" else "kotlogramme"
val nativeLibraryName = when (nativeOs) {
    "windows" -> "kotlogramme.dll"
    "macos" -> "libkotlogramme.dylib"
    else -> "libkotlogramme.so"
}
val nativeMetadataDir = layout.buildDirectory.dir("generated/native-metadata")
val generateNativeMetadata = tasks.register("generateNativeMetadata") {
    val runtimeClasspath = configurations.runtimeClasspath
    inputs.files(runtimeClasspath)
    inputs.property("platform", nativePlatform)
    outputs.dir(nativeMetadataDir)
    doLast {
        // Files from an earlier build, such as a resource list that no longer exists, must not linger.
        nativeMetadataDir.get().asFile.deleteRecursively()
        val facadeJar = runtimeClasspath.get().single { it.name.startsWith("kotlogram-") }
        val registrations = ZipFile(facadeJar).use { archive ->
            archive.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".class") }
                .filter { it.startsWith("org/kotlogramme/protocol/") || it.startsWith("org/kotlogramme/raw/") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .sorted()
                .map { name ->
                    """{"name":"$name","allDeclaredFields":true,"allDeclaredMethods":true,""" +
                        """"allDeclaredConstructors":true,"allDeclaredClasses":true}"""
                }
                .toList()
        }
        nativeMetadataDir.get().file("reflect-config.json").asFile.apply {
            parentFile.mkdirs()
            writeText(registrations.joinToString(",\n", "[\n", "\n]\n"))
        }
    }
}

graalvmNative {
    // GRAALVM_HOME selects the installation independently of the JVM running Gradle.
    toolchainDetection.set(false)
    binaries {
        named("main") {
            imageName.set("kotlogramme")
            configurationFileDirectories.from(nativeMetadataDir)
            buildArgs.addAll(
                "--no-fallback",
                "-march=compatibility",
                "-Os",
                "--enable-native-access=ALL-UNNAMED",
                "--initialize-at-run-time=org.kotlogramme.NativeLibraryLoader,org.jline.nativ.NativeLibraryLoader",
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
            )
        }
    }
}

tasks.named("nativeCompile") {
    dependsOn(generateNativeMetadata)
}

// The executable does not embed the facade's native library. It sits beside the executable, where
// the single-file packaging unpacks it once and keeps it, and the client loads it from there (see
// SidecarNativeLibrary) instead of copying it into a new temporary directory on every start.
val extractNativeLibrary = tasks.register<Copy>("extractNativeLibrary") {
    description = "Places the facade's native library beside the native executable."
    dependsOn(tasks.named("nativeCompile"))
    val facadeJar = configurations.runtimeClasspath.map { classpath ->
        classpath.single { it.name.startsWith("kotlogram-") }
    }
    from(facadeJar.map { zipTree(it) }) {
        include("native/$nativePlatform/$nativeLibraryName")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("native/nativeCompile"))
}

tasks.register<Exec>("nativeSmokeTest") {
    group = "verification"
    description = "Checks the native distribution offline with Java removed from its environment."
    dependsOn(extractNativeLibrary)
    commandLine("pwsh", "-NoProfile", "-File", file("scripts/test-native.ps1").absolutePath)
}

// The native library, and any JDK support libraries the image still needs, go beside the executable.
val nativeDistributionFiles = fileTree(layout.buildDirectory.dir("native/nativeCompile")) {
    include(nativeExecutableName, "*.dll", "*.so", "*.so.*", "*.dylib")
}

tasks.register<Zip>("nativeDistZip") {
    group = "distribution"
    description = "Packages the native executable, support libraries and license notices as a zip."
    dependsOn(tasks.named("nativeSmokeTest"))
    archiveFileName.set("kotlogramme-$version-$nativePlatform.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(nativeDistributionFiles)
    from("LICENSE", "NOTICE")
    filesMatching(nativeExecutableName) {
        permissions { unix("755") }
    }
}

tasks.register<Tar>("nativeDistTar") {
    group = "distribution"
    description = "Packages the native executable, support libraries and license notices as a tarball."
    dependsOn(tasks.named("nativeSmokeTest"))
    compression = Compression.GZIP
    archiveFileName.set("kotlogramme-$version-$nativePlatform.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(nativeDistributionFiles)
    from("LICENSE", "NOTICE")
    filesMatching(nativeExecutableName) {
        permissions { unix("755") }
    }
}

tasks.register("nativeDist") {
    group = "distribution"
    description = "Builds and verifies the native package for the host platform."
    dependsOn(if (nativeOs == "windows") "nativeDistZip" else "nativeDistTar")
}
tasks.register<Exec>("nativePackagingTest") {
    group = "verification"
    description = "Checks that single-file selection ignores licenses and includes every native library."
    commandLine("pwsh", "-NoProfile", "-File", file("scripts/test-native-packaging.ps1").absolutePath)
}

tasks.register<Exec>("nativeSingle") {
    group = "distribution"
    description = "Creates and tests one portable executable with Wrappe when native libraries are needed."
    dependsOn(tasks.named("nativeSmokeTest"), tasks.named("nativePackagingTest"))
    commandLine(
        "pwsh", "-NoProfile", "-File", file("scripts/build-native-single.ps1").absolutePath,
        "-NativeDirectory", layout.buildDirectory.dir("native/nativeCompile").get().asFile.absolutePath,
        "-OutputDirectory", layout.buildDirectory.dir("distributions").get().asFile.absolutePath,
        "-Platform", nativePlatform,
        "-Version", version.toString(),
    )
    providers.environmentVariable("WRAPPE_BIN").orNull?.let { args("-Wrappe", it) }
}