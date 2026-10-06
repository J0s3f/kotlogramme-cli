package org.kotlogramme.cli.adapter.diagnostic

import com.github.ajalt.mordant.terminal.Terminal as MordantTerminal
import org.jline.terminal.Terminal as JlineTerminal
import org.jline.terminal.TerminalBuilder
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import java.nio.charset.Charset

/**
 * Loads the two terminal libraries and reports what they found.
 *
 * JLine drives the interactive shell and Mordant draws tables and colour; both reach the operating
 * system through native code in an image, so opening each one is the test that it works. A terminal
 * that cannot be opened is a warning: one-shot commands do not need it, only `shell` does.
 */
class TerminalDiagnostic(
    private val openJline: () -> JlineTerminal = { TerminalBuilder.builder().system(true).dumb(true).build() },
    private val encoding: () -> String = ::outputEncoding,
) : Diagnostic {
    override val name = "Terminal"

    override fun run(): Finding {
        val jline = runCatching { openJline().use { "JLine ${it.type}" } }
        val mordant = runCatching { describeMordant() }
        val output = encoding()
        val detail = listOf(
            jline.getOrElse { "JLine unavailable: ${it.message ?: it::class.simpleName}" },
            mordant.getOrElse { "Mordant unavailable: ${it.message ?: it::class.simpleName}" },
            "output encoding $output",
        ).joinToString("; ")
        val healthy = jline.isSuccess && mordant.isSuccess && output.isUtf8()
        return if (healthy) Finding.ok(detail) else Finding.warning(detail)
    }

    private fun describeMordant(): String {
        val terminal = MordantTerminal()
        return "Mordant ${terminal.terminalInfo.ansiLevel.name.lowercase()} colour, ${terminal.size.width} columns"
    }

    private fun String.isUtf8(): Boolean = equals("UTF-8", ignoreCase = true)
}

private fun outputEncoding(): String = System.getProperty("stdout.encoding") ?: Charset.defaultCharset().name()
