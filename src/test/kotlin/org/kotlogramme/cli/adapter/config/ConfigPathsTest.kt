package org.kotlogramme.cli.adapter.config

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigPathsTest {

    private val home: Path = Paths.get("home", "user")

    @Test
    fun `linux honours XDG_CONFIG_HOME`() {
        val paths = ConfigPaths("Linux", mapOf("XDG_CONFIG_HOME" to "/xdg/config"), home)

        assertEquals(Paths.get("/xdg/config", "kotlogramme"), paths.baseDir())
    }

    @Test
    fun `linux falls back to the dot config directory`() {
        val paths = ConfigPaths("Linux", emptyMap(), home)

        assertEquals(home.resolve(".config").resolve("kotlogramme"), paths.baseDir())
    }

    @Test
    fun `macos uses the application support directory`() {
        val paths = ConfigPaths("Mac OS X", emptyMap(), home)

        assertEquals(
            home.resolve("Library").resolve("Application Support").resolve("kotlogramme"),
            paths.baseDir(),
        )
    }

    @Test
    fun `windows uses APPDATA`() {
        val appData = "C:\\Users\\user\\AppData\\Roaming"
        val paths = ConfigPaths("Windows 11", mapOf("APPDATA" to appData), home)

        assertEquals(Paths.get(appData, "kotlogramme"), paths.baseDir())
    }

    @Test
    fun `the kotlogramme configDir property overrides the platform rule`() {
        val override = Paths.get("custom", "config").toAbsolutePath()
        System.setProperty(ConfigPaths.CONFIG_DIR_PROPERTY, override.toString())
        try {
            val paths = ConfigPaths("Linux", emptyMap(), home)

            assertEquals(override, paths.baseDir())
        } finally {
            System.clearProperty(ConfigPaths.CONFIG_DIR_PROPERTY)
        }
    }
}
