package org.kotlogramme.cli.adapter.cli

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.adapter.config.JsonConfigStore
import org.kotlogramme.cli.adapter.format.TableWidth
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The production composition root, built against a temporary configuration directory. */
class AppContextCreateTest {
    @field:TempDir
    private lateinit var tempDir: Path

    private fun create(
        options: GlobalOptions = GlobalOptions(configDir = tempDir),
        environment: Map<String, String> = emptyMap(),
    ) = AppContext.create(options, environment)

    @Test
    fun `the configuration directory is the one given`() {
        assertEquals(tempDir, create().configDir)
    }

    @Test
    fun `creating the context writes nothing to the configuration directory`() {
        val missing = tempDir.resolve("not-yet")

        create(GlobalOptions(configDir = missing))

        assertTrue(Files.notExists(missing))
    }

    @Test
    fun `without a stored format the output is a table`() {
        val writer = StringWriter()

        create().outputOn(writer).table(listOf("id"), listOf(listOf("1")))

        assertTrue(writer.toString().startsWith("+"), writer.toString())
    }

    @Test
    fun `the stored output format decides how the output renders`() {
        val store = JsonConfigStore(tempDir)
        store.save(store.load().copy(outputFormat = OutputFormat.JSON))
        val writer = StringWriter()

        create().outputOn(writer).table(listOf("id"), listOf(listOf("1")))

        assertEquals("""[{"id":"1"}]""", writer.toString().trim())
    }

    @Test
    fun `a run that is not on a terminal is not interactive`() {
        assertFalse(create().isInteractiveTerminal)
    }

    @Test
    fun `the table width option reaches the output`() {
        val writer = StringWriter()

        create(GlobalOptions(configDir = tempDir, tableWidth = TableWidth.Fixed(20)))
            .outputOn(writer)
            .table(listOf("text"), listOf(listOf("word ".repeat(20).trim())))

        assertTrue(writer.toString().lines().all { it.length <= 20 }, writer.toString())
    }

    @Test
    fun `credentials come from the environment when the config has none`() {
        val context = create(environment = mapOf("TG_API_ID" to "12345", "TG_API_HASH" to "abc"))

        assertEquals(ApiCredentials(apiId = 12345, apiHash = "abc"), context.credentials())
    }

    @Test
    fun `stored credentials win over the environment`() {
        val store = JsonConfigStore(tempDir)
        store.save(store.load().copy(credentials = ApiCredentials(apiId = 1, apiHash = "stored")))
        val context = create(environment = mapOf("TG_API_ID" to "2", "TG_API_HASH" to "env"))

        assertEquals(ApiCredentials(apiId = 1, apiHash = "stored"), context.credentials())
    }

    @Test
    fun `no credentials anywhere is none`() {
        assertNull(create().credentials())
    }

    @Test
    fun `a command that needs Telegram without credentials is told how to set them`() {
        val error = assertFailsWith<MissingCredentialsError> { create().authenticate() }

        assertTrue(error.message.orEmpty().contains("config set"), error.message)
    }
}
