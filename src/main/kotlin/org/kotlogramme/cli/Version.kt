package org.kotlogramme.cli

/**
 * The version reported by `--version`.
 *
 * The build bakes the Gradle project version into the `kotlogramme-version.properties` resource, so
 * a release built with `-Pversion=0.1.0` reports `0.1.0` and a local build without that property
 * reports the development default. Keeping the value in the build keeps the two in agreement.
 */
val VERSION: String = Version.resolve()

internal object Version {
    private const val RESOURCE = "kotlogramme-version.properties"
    private const val DEVELOPMENT_VERSION = "0.1.0-SNAPSHOT"

    fun resolve(): String {
        val baked = Version::class.java.classLoader
            .getResourceAsStream(RESOURCE)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader ->
                reader.readLines()
                    .firstOrNull { it.startsWith("version=") }
                    ?.removePrefix("version=")
                    ?.trim()
            }
        return baked?.takeIf { it.isNotEmpty() } ?: DEVELOPMENT_VERSION
    }
}
