package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.kotlogramme.cli.KotlogrammeCommand
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `--table`, `--plain` and `--json` are global options that override the configured format for one run. */
class FormatOptionTest {
    private var received: GlobalOptions? = null

    private val testConfig = AppConfig(
        credentials = ApiCredentials(apiId = 1, apiHash = "configured-hash"),
        sessionPath = Paths.get("session.sqlite"),
    )

    private fun parse(vararg arguments: String) = KotlogrammeCommand { options ->
        received = options
        AppContext(
            configDir = Paths.get("config"),
            configStore = FakeConfigStore(testConfig),
            output = RecordingOutput(),
            environment = emptyMap(),
        )
    }.subcommands(DoctorCommand { emptyList() }).test(arguments.toList())

    @Test
    fun `without an option the configured format stays in charge`() {
        parse("doctor")

        assertNull(received?.format)
    }

    @Test
    fun `json selects the json format`() {
        parse("--json", "doctor")

        assertEquals(OutputFormat.JSON, received?.format)
    }

    @Test
    fun `plain selects the plain format`() {
        parse("--plain", "doctor")

        assertEquals(OutputFormat.PLAIN, received?.format)
    }

    @Test
    fun `table selects the table format`() {
        parse("--table", "doctor")

        assertEquals(OutputFormat.TABLE, received?.format)
    }

    @Test
    fun `the last format option wins`() {
        parse("--json", "--plain", "doctor")

        assertEquals(OutputFormat.PLAIN, received?.format)
    }

    @Test
    fun `the options are on the command's help`() {
        val help = KotlogrammeCommand { error("not built for help") }.test(listOf("--help")).stdout

        assertTrue(help.contains("--json"), help)
        assertTrue(help.contains("--plain"), help)
        assertTrue(help.contains("--table"), help)
    }
}
