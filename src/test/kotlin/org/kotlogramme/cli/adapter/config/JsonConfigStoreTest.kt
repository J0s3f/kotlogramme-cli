package org.kotlogramme.cli.adapter.config

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonConfigStoreTest {

    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `first load creates the directory and returns defaults`() {
        val baseDir = tempDir.resolve("kotlogramme")
        val store = JsonConfigStore(baseDir)

        val config = store.load()

        assertNull(config.credentials)
        assertEquals(baseDir.resolve("session.sqlite"), config.sessionPath)
        assertEquals(OutputFormat.TABLE, config.outputFormat)
        assertTrue(Files.isDirectory(baseDir))
    }

    @Test
    fun `save writes a file that load reads back unchanged`() {
        val baseDir = tempDir.resolve("kotlogramme")
        val store = JsonConfigStore(baseDir)
        val config = AppConfig(
            credentials = ApiCredentials(apiId = 12345, apiHash = "deadbeef"),
            sessionPath = tempDir.resolve("custom-session.sqlite"),
            outputFormat = OutputFormat.JSON,
        )

        store.save(config)

        assertTrue(Files.exists(baseDir.resolve("config.json")))
        assertEquals(config, store.load())
    }

    @Test
    fun `save creates the directory when missing`() {
        val baseDir = tempDir.resolve("nested").resolve("kotlogramme")
        val store = JsonConfigStore(baseDir)

        store.save(AppConfig(credentials = null, sessionPath = baseDir.resolve("session.sqlite")))

        assertTrue(Files.isDirectory(baseDir))
    }

    @Test
    fun `a malformed file names the file in the error`() {
        val baseDir = tempDir.resolve("kotlogramme")
        Files.createDirectories(baseDir)
        Files.writeString(baseDir.resolve("config.json"), "{ this is not json")
        val store = JsonConfigStore(baseDir)

        val error = assertFailsWith<IllegalStateException> { store.load() }

        assertTrue(error.message!!.contains("config.json"), "message was: ${error.message}")
    }
}
