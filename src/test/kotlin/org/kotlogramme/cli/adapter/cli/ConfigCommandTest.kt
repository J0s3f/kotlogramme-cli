package org.kotlogramme.cli.adapter.cli

import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.adapter.config.JsonConfigStore
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigCommandTest {
    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `config shows the resolved settings without the api hash`() {
        val session = tempDir.resolve("session.sqlite")
        val fixture = cliFixture(
            config = AppConfig(ApiCredentials(555, "super-secret-hash"), session, OutputFormat.JSON),
            configDir = tempDir,
        )

        val result = fixture.run("config")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "Config directory: $tempDir",
                "Session path: $session",
                "Credentials: set (API id 555)",
                "Output format: json",
            ),
            fixture.output.lines,
        )
        assertFalse(fixture.output.text.contains("super-secret-hash"))
    }

    @Test
    fun `config falls back to the environment credentials`() {
        val fixture = cliFixture(
            config = AppConfig(credentials = null, sessionPath = tempDir.resolve("session.sqlite")),
            configDir = tempDir,
            environment = mapOf("TG_API_ID" to "7", "TG_API_HASH" to "env-secret"),
        )

        fixture.run("config")

        assertTrue(fixture.output.text.contains("Credentials: set (API id 7)"))
        assertFalse(fixture.output.text.contains("env-secret"))
    }

    @Test
    fun `config set persists the credentials and the format`() {
        val store = JsonConfigStore(tempDir)
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set", "--api-id", "12345", "--api-hash", "deadbeef", "--format", "json")

        assertEquals(0, result.statusCode)
        val saved = store.load()
        assertEquals(ApiCredentials(12345, "deadbeef"), saved.credentials)
        assertEquals(OutputFormat.JSON, saved.outputFormat)
    }

    @Test
    fun `config set without a format keeps the stored one`() {
        val store = JsonConfigStore(tempDir)
        store.save(
            AppConfig(
                credentials = null,
                sessionPath = tempDir.resolve("session.sqlite"),
                outputFormat = OutputFormat.PLAIN,
            ),
        )
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        fixture.run("config", "set", "--api-id", "1", "--api-hash", "hash")

        assertEquals(OutputFormat.PLAIN, store.load().outputFormat)
    }

    @Test
    fun `config set with only a format preserves the stored credentials`() {
        val store = JsonConfigStore(tempDir)
        store.save(
            AppConfig(
                credentials = ApiCredentials(33582468, "stored-hash"),
                sessionPath = tempDir.resolve("session.sqlite"),
                outputFormat = OutputFormat.PLAIN,
            ),
        )
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set", "--format=json")

        assertEquals(0, result.statusCode)
        val saved = store.load()
        assertEquals(ApiCredentials(33582468, "stored-hash"), saved.credentials)
        assertEquals(OutputFormat.JSON, saved.outputFormat)
    }

    @Test
    fun `config set with only an api id preserves the stored hash and format`() {
        val store = JsonConfigStore(tempDir)
        store.save(
            AppConfig(
                credentials = ApiCredentials(1, "stored-hash"),
                sessionPath = tempDir.resolve("session.sqlite"),
                outputFormat = OutputFormat.JSON,
            ),
        )
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set", "--api-id", "42")

        assertEquals(0, result.statusCode)
        val saved = store.load()
        assertEquals(ApiCredentials(42, "stored-hash"), saved.credentials)
        assertEquals(OutputFormat.JSON, saved.outputFormat)
    }

    @Test
    fun `config set cannot change half the credentials before anything is stored`() {
        val store = JsonConfigStore(tempDir)
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set", "--api-id", "42")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("pass both --api-id and --api-hash together"))
        assertEquals(null, store.load().credentials)
    }

    @Test
    fun `config set rejects an unknown format`() {
        val store = JsonConfigStore(tempDir)
        store.save(
            AppConfig(
                credentials = ApiCredentials(1, "stored-hash"),
                sessionPath = tempDir.resolve("session.sqlite"),
            ),
        )
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set", "--format", "yaml")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("Unknown output format 'yaml'; choose table, plain or json"))
        assertEquals(OutputFormat.entries.first(), store.load().outputFormat)
    }

    @Test
    fun `config set without options is a usage error and writes nothing`() {
        val store = JsonConfigStore(tempDir)
        store.save(
            AppConfig(
                credentials = ApiCredentials(1, "stored-hash"),
                sessionPath = tempDir.resolve("session.sqlite"),
                outputFormat = OutputFormat.PLAIN,
            ),
        )
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("give at least one of --api-id, --api-hash or --format"))
        val saved = store.load()
        assertEquals(ApiCredentials(1, "stored-hash"), saved.credentials)
        assertEquals(OutputFormat.PLAIN, saved.outputFormat)
    }

    @Test
    fun `config set help does not demand the credential options`() {
        val store = JsonConfigStore(tempDir)
        val fixture = cliFixture(configStore = store, configDir = tempDir)

        val result = fixture.run("config", "set", "--help")

        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("--api-id"))
        assertTrue(result.output.contains("--format"))
    }
}
