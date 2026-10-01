package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Session
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionRenderingTest {
    private val sessions = listOf(
        Session(
            hash = 11,
            deviceModel = "Desktop",
            platform = "Windows",
            appVersion = "0.4.0",
            ip = "203.0.113.7",
            country = "AT",
            createdAt = Instant.parse("2026-01-01T12:30:00Z"),
            current = true,
        ),
        Session(
            hash = 22,
            deviceModel = "Phone",
            platform = "Android",
            appVersion = "10.2",
            ip = "198.51.100.4",
            country = "DE",
            createdAt = Instant.parse("2026-01-02T08:00:00Z"),
            current = false,
        ),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `plain sessions are tab separated with a current marker`() {
        val rendered = render(OutputFormat.PLAIN) { renderSessions(sessions) }

        assertEquals(
            listOf(
                "current\thash\tdevice\tplatform\tapp\tip\tcountry\tcreated",
                "yes\t11\tDesktop\tWindows\t0.4.0\t203.0.113.7\tAT\t2026-01-01T12:30:00Z",
                "\t22\tPhone\tAndroid\t10.2\t198.51.100.4\tDE\t2026-01-02T08:00:00Z",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json sessions are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderSessions(listOf(sessions.first())) }

        assertEquals(
            """[{"current":"yes","hash":"11","device":"Desktop","platform":"Windows",""" +
                """"app":"0.4.0","ip":"203.0.113.7","country":"AT","created":"2026-01-01T12:30:00Z"}]""",
            rendered,
        )
    }
}
