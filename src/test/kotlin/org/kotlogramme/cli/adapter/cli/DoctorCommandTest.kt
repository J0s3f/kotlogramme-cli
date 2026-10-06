package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Finding
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoctorCommandTest {
    private fun diagnostic(name: String, finding: Finding) = object : Diagnostic {
        override val name = name

        override fun run() = finding
    }

    private val noCredentials = AppConfig(credentials = null, sessionPath = Paths.get("session.sqlite"))

    @Test
    fun `doctor prints one row per check`() {
        val fixture = cliFixture(
            config = noCredentials.copy(outputFormat = OutputFormat.PLAIN),
            diagnostics = listOf(
                diagnostic("Runtime", Finding.ok("kotlogramme 1.0")),
                diagnostic("Network", Finding.warning("offline")),
                diagnostic("Account", Finding.skipped("needs API credentials")),
            ),
        )

        val result = fixture.run("doctor")

        assertEquals(0, result.statusCode, result.stderr)
        assertEquals(
            listOf(
                "check\tstatus\tdetail",
                "Runtime\tok\tkotlogramme 1.0",
                "Network\twarning\toffline",
                "Account\tskipped\tneeds API credentials",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `doctor runs without any credentials`() {
        val fixture = cliFixture(
            config = noCredentials,
            diagnostics = listOf(diagnostic("Runtime", Finding.ok("fine"))),
        )

        val result = fixture.run("doctor")

        assertEquals(0, result.statusCode, result.stderr)
        assertTrue(result.stderr.isEmpty(), result.stderr)
    }

    @Test
    fun `doctor runs with credentials too`() {
        val fixture = cliFixture(
            config = AppConfig(ApiCredentials(7, "hash"), Paths.get("session.sqlite")),
            diagnostics = listOf(diagnostic("Runtime", Finding.ok("fine"))),
        )

        assertEquals(0, fixture.run("doctor").statusCode)
    }

    @Test
    fun `warnings and skipped checks do not fail the command`() {
        val fixture = cliFixture(
            config = noCredentials,
            diagnostics = listOf(
                diagnostic("Network", Finding.warning("offline")),
                diagnostic("Account", Finding.skipped("needs API credentials")),
            ),
        )

        assertEquals(0, fixture.run("doctor").statusCode)
    }

    @Test
    fun `a failed check fails the command after the whole report is printed`() {
        val fixture = cliFixture(
            config = noCredentials.copy(outputFormat = OutputFormat.PLAIN),
            diagnostics = listOf(
                diagnostic("Native library", Finding.failed("no build for this platform")),
                diagnostic("Runtime", Finding.ok("fine")),
            ),
        )

        val result = fixture.run("doctor")

        assertEquals(1, result.statusCode)
        assertEquals(3, fixture.output.lines.size, fixture.output.lines.toString())
        assertTrue(result.stderr.contains("1 of 2 checks failed"), "stderr was: ${result.stderr}")
    }

    @Test
    fun `a check that throws is reported and the command fails without a stack trace`() {
        val broken = object : Diagnostic {
            override val name = "Broken"

            override fun run(): Finding = throw UnsatisfiedLinkError("no kotlogramme")
        }
        val fixture = cliFixture(config = noCredentials, diagnostics = listOf(broken))

        val result = fixture.run("doctor")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("1 of 1 checks failed"), "stderr was: ${result.stderr}")
        assertTrue(!result.stderr.contains("UnsatisfiedLinkError"), result.stderr)
    }

    @Test
    fun `the standard checks cover the installation, the library and the account`() {
        val context = AppContext(
            configDir = Paths.get("config"),
            configStore = FakeConfigStore(noCredentials),
            output = RecordingOutput(),
            environment = emptyMap(),
        )

        val names = standardDiagnostics(context).map { it.name }

        assertEquals(
            listOf(
                "Runtime",
                "Native library",
                "Telegram schema",
                "Terminal",
                "Temporary directory",
                "Configuration",
                "Credentials",
                "Session",
                "Network",
                "Account",
            ),
            names,
        )
    }
}
