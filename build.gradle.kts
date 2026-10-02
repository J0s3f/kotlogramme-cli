import org.gradle.api.file.DuplicatesStrategy

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
    id("com.gradleup.shadow") version "9.6.1"
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
