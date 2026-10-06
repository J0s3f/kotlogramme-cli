package org.kotlogramme.cli.adapter.diagnostic

import org.jline.terminal.TerminalBuilder
import org.jline.terminal.spi.SystemStream
import org.jline.terminal.spi.TerminalProvider
import org.kotlogramme.cli.domain.DiagnosticStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TerminalDiagnosticTest {
    @Test
    fun `the real terminal libraries load without failing the check`() {
        val finding = TerminalDiagnostic().run()

        assertNotEquals(DiagnosticStatus.FAILED, finding.status, finding.detail)
        assertTrue(finding.detail.contains("JLine"), finding.detail)
        assertTrue(finding.detail.contains("Mordant"), finding.detail)
    }

    @Test
    fun `a terminal that cannot be opened is a warning because commands still work`() {
        val finding = TerminalDiagnostic(openJline = { error("no terminal provider") }).run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("no terminal provider"), finding.detail)
    }

    @Test
    fun `names the terminal implementation JLine chose on any platform`() {
        val open = {
            TerminalBuilder.builder()
                .system(false)
                .type("ansi")
                .streams(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())
                .build()
        }

        val finding = TerminalDiagnostic(openJline = open).run()

        assertTrue(Regex("""JLine ansi \(\w+\)""").containsMatchIn(finding.detail), finding.detail)
    }

    @Test
    fun `reports that the FFM provider works when it can ask the operating system`() {
        val provider = object : TerminalProvider by TerminalProvider.load("dumb") {
            override fun isSystemStream(stream: SystemStream) = false
        }

        val finding = TerminalDiagnostic(loadProvider = { provider }).run()

        assertTrue(finding.detail.contains("JLine FFM provider ok"), finding.detail)
    }

    @Test
    fun `an FFM provider that cannot be loaded is a warning because the shell would fall back`() {
        val finding = TerminalDiagnostic(loadProvider = { error("no ffm in this image") }).run()

        assertEquals(DiagnosticStatus.WARNING, finding.status)
        assertTrue(finding.detail.contains("FFM provider unavailable: no ffm in this image"), finding.detail)
    }

    @Test
    fun `reports the output encoding`() {
        val finding = TerminalDiagnostic(encoding = { "UTF-8" }).run()

        assertTrue(finding.detail.contains("UTF-8"), finding.detail)
    }
}
