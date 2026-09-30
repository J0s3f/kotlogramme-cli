plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "io.github.j0s3f"
version = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT").get()

// The facade this client exercises. Override with -PkotlogrammeVersion=... while a release is
// still being validated on Maven Central.
val kotlogrammeVersion = providers.gradleProperty("kotlogrammeVersion").orElse("0.2.0").get()

// This application is not a library anyone links against, so it targets the newest LTS JVM and the
// newest stable Kotlin rather than the conservative versions the facade is bound to.
kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("io.github.j0s3f:kotlogramme:$kotlogrammeVersion")
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
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
