rootProject.name = "kotlogramme-cli"

plugins {
    // Lets Gradle fetch the JDK the build asks for on a machine that lacks it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // -PuseMavenLocal resolves a locally published kotlogramme while the facade and the client
        // are developed together; CI and ordinary builds stay on Maven Central.
        if (gradle.startParameter.projectProperties.containsKey("useMavenLocal")) {
            mavenLocal()
        }
    }
}
