package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.kotlogramme.cli.KotlogrammeCommand
import org.kotlogramme.cli.adapter.format.TableWidth
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** `--table-width` is a global option: it comes before the command name and reaches the composition root. */
class TableWidthOptionTest {
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
    fun `without the option the width is detected`() {
        parse("doctor")

        assertEquals(TableWidth.Detect, received?.tableWidth)
    }

    @Test
    fun `a number fixes the width`() {
        parse("--table-width", "72", "doctor")

        assertEquals(TableWidth.Fixed(72), received?.tableWidth)
    }

    @Test
    fun `zero turns wrapping off`() {
        parse("--table-width", "0", "doctor")

        assertEquals(TableWidth.Unlimited, received?.tableWidth)
    }

    @Test
    fun `a negative width is a usage error`() {
        val result = parse("--table-width", "-5", "doctor")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("table-width"), result.stderr)
    }

    @Test
    fun `a width that is not a number is a usage error`() {
        val result = parse("--table-width", "wide", "doctor")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("table-width"), result.stderr)
    }

    @Test
    fun `the option sits beside the other global options`() {
        parse("--no-color", "--table-width", "90", "--config-dir", "somewhere", "doctor")

        val options = requireNotNull(received)
        assertTrue(options.noColor)
        assertEquals(TableWidth.Fixed(90), options.tableWidth)
        assertEquals(Paths.get("somewhere"), options.configDir)
    }

    @Test
    fun `the option is on the command's help`() {
        val result = KotlogrammeCommand { error("not built for help") }.test(listOf("--help"))

        assertTrue(result.stdout.contains("--table-width"), result.stdout)
    }

    @Test
    fun `a width given to the context wraps the tables of an output on a writer`() {
        val context = AppContext(
            configDir = Paths.get("config"),
            configStore = FakeConfigStore(testConfig),
            output = RecordingOutput(),
            environment = emptyMap(),
            outputFormat = OutputFormat.TABLE,
            tableWidth = TableWidth.Fixed(30),
        )
        val writer = java.io.StringWriter()

        context.outputOn(writer).table(listOf("id", "text"), listOf(listOf("1", "word ".repeat(20).trim())))

        val lines = writer.toString().lines().filter(String::isNotEmpty)
        assertTrue(lines.all { it.length <= 30 }, lines.toString())
    }

    @Test
    fun `an unlimited width keeps the shell's tables whole whatever the terminal says`() {
        val context = AppContext(
            configDir = Paths.get("config"),
            configStore = FakeConfigStore(testConfig),
            output = RecordingOutput(),
            environment = emptyMap(),
            outputFormat = OutputFormat.TABLE,
            tableWidth = TableWidth.Unlimited,
        )
        val writer = java.io.StringWriter()

        context.outputOn(writer) { 30 }.table(listOf("id", "text"), listOf(listOf("1", "word ".repeat(20).trim())))

        assertEquals(5, writer.toString().lines().count(String::isNotEmpty))
    }

    @Test
    fun `without an option the shell's tables fit the terminal it reports`() {
        val context = AppContext(
            configDir = Paths.get("config"),
            configStore = FakeConfigStore(testConfig),
            output = RecordingOutput(),
            environment = emptyMap(),
            outputFormat = OutputFormat.TABLE,
        )
        val writer = java.io.StringWriter()

        context.outputOn(writer) { 30 }.table(listOf("id", "text"), listOf(listOf("1", "word ".repeat(20).trim())))

        assertTrue(writer.toString().lines().filter(String::isNotEmpty).all { it.length <= 30 })
    }
}
