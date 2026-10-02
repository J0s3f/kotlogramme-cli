import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "kotlogramme-cli"

plugins {
    // Lets Gradle fetch the JDK the build asks for on a machine that lacks it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // -PuseMavenLocal resolves a locally published kotlogramme while the facade and the client
        // are developed together; CI and ordinary builds stay on JitPack. It comes first so a machine
        // with no network reaches the local copy instead of failing on Maven Central's DNS.
        if (gradle.startParameter.projectProperties.containsKey("useMavenLocal")) {
            mavenLocal()
        }
        mavenCentral()
        // A facade tag reaches JitPack before Maven Central catches up, so the current facade is
        // resolved from here; Maven Central stays first for everything already published.
        maven { url = uri("https://jitpack.io") }
    }
}
