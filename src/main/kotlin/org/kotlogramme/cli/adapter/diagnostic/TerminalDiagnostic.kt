package org.kotlogramme.cli.adapter.diagnostic

import com.github.ajalt.mordant.terminal.Terminal as MordantTerminal
import org.jline.terminal.Terminal as JlineTerminal
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.spi.SystemStream
import org.jline.terminal.spi.TerminalProvider
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
    private val loadProvider: (String) -> TerminalProvider = TerminalProvider::load,
    private val encoding: () -> String = ::outputEncoding,
) : Diagnostic {
    override val name = "Terminal"

    override fun run(): Finding {
        val ffm = runCatching { checkFfmProvider() }
        val jline = runCatching { openJline().use { "JLine ${it.type} (${it.javaClass.simpleName})" } }
        val mordant = runCatching { describeMordant() }
        val output = encoding()
        val detail = listOf(
            jline.getOrElse { "JLine unavailable: ${it.message ?: it::class.simpleName}" },
            ffm.getOrElse { "JLine FFM provider unavailable: ${rootCause(it)}" },
            mordant.getOrElse { "Mordant unavailable: ${it.message ?: it::class.simpleName}" },
            "output encoding $output",
        ).joinToString("; ")
        val healthy = jline.isSuccess && ffm.isSuccess && mordant.isSuccess && output.isUtf8()
        return if (healthy) Finding.ok(detail) else Finding.warning(detail)
    }

    /**
     * Loads JLine's FFM provider and asks it whether stdout is a terminal, which calls into the
     * operating system. The provider needs no library file, so this also shows that a native image
     * does not depend on JLine's JNI helper libraries.
     */
    private fun checkFfmProvider(): String {
        loadProvider(FFM_PROVIDER).isSystemStream(SystemStream.Output)
        return "JLine FFM provider ok"
    }

    private fun describeMordant(): String {
        val terminal = MordantTerminal()
        return "Mordant ${terminal.terminalInfo.ansiLevel.name.lowercase()} colour, ${terminal.size.width} columns"
    }

    private fun String.isUtf8(): Boolean = equals("UTF-8", ignoreCase = true)

    private companion object {
        const val FFM_PROVIDER = "ffm"
    }
}

private fun outputEncoding(): String = System.getProperty("stdout.encoding") ?: Charset.defaultCharset().name()

/** The innermost cause, which is what a failed class initialisation hides behind a generic message. */
private fun rootCause(error: Throwable): String {
    val cause = generateSequence(error) { it.cause }.last()
    return cause.message ?: cause::class.simpleName ?: cause.toString()
}
