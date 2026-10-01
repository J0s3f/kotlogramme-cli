package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.io.StringWriter
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The composition root is where the terminal writer is swapped in for `System.out`. These tests pin
 * the two halves of that seam: the terminal-backed output writes through the writer it was given,
 * and the fallback output is the one the context was built with.
 */
class AppContextOutputTest {
    private fun context(format: OutputFormat = OutputFormat.PLAIN): AppContext = AppContext(
        configDir = Paths.get("config"),
        configStore = FakeConfigStore(testConfig),
        output = RecordingOutput(),
        environment = emptyMap(),
        outputFormat = format,
    )

    @Test
    fun `an output on a writer renders the same format as the fallback`() {
        val writer = StringWriter()

        context(OutputFormat.PLAIN).outputOn(writer).table(listOf("id", "name"), listOf(listOf("1", "josef")))

        assertEquals(listOf("id\tname", "1\tjosef"), writer.toString().lines().filter(String::isNotEmpty))
    }

    @Test
    fun `non-ascii text reaches the writer without an encoder in between`() {
        val text = "Привет, мир! 你好 مرحبا 😀🎉"
        val writer = StringWriter()

        context().outputOn(writer).line(text)

        assertTrue(writer.toString().contains(text), "expected the text intact in '${writer}'")
    }

    @Test
    fun `the fallback output is untouched when no writer is supplied`() {
        val output = RecordingOutput()
        val context = AppContext(
            configDir = Paths.get("config"),
            configStore = FakeConfigStore(testConfig),
            output = output,
            environment = emptyMap(),
        )

        context.output.line("fallback")

        assertEquals(listOf("fallback"), output.lines)
    }

    private companion object {
        val testConfig = AppConfig(
            credentials = ApiCredentials(apiId = 1, apiHash = "configured-hash"),
            sessionPath = Paths.get("session.sqlite"),
        )
    }
}
