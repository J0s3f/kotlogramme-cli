import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Compression
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
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

application {
    applicationName = "kotlogramme"
    mainClass.set("org.kotlogramme.cli.MainKt")
    // JLine reaches its native terminal support through JNA; JDK 24+ warns unless native access is
    // enabled, and a future release blocks it outright.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        // Windows defaults stdout/stderr to the ANSI code page, which turns every non-ASCII
        // character into `?`; the client is UTF-8 end to end.
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

tasks.test {
    useJUnitPlatform()
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
        nativeMetadataDir.get().file("resource-config.json").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """{"resources":{"includes":[{"pattern":"native/$nativePlatform/$nativeLibraryName"}]}}""",
            )
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

tasks.register<Exec>("nativeSmokeTest") {
    group = "verification"
    description = "Checks the native distribution offline with Java removed from its environment."
    dependsOn(tasks.named("nativeCompile"))
    commandLine("pwsh", "-NoProfile", "-File", file("scripts/test-native.ps1").absolutePath)
}

// Keep the image's AWT support libraries alongside the executable: image probing uses ImageIO.
val nativeDistributionFiles = fileTree(layout.buildDirectory.dir("native/nativeCompile")) {
    include(nativeExecutableName, "*.dll", "*.so", "*.dylib")
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