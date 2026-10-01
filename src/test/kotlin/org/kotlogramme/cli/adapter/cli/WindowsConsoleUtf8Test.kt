package org.kotlogramme.cli.adapter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the decision of whether the one-shot output is switched to UTF-8. The fix mutates a shared
 * console, so the tests are mostly about *not* acting: redirected output, another platform and an
 * already-correct console must all be left completely alone. A fake code page records every call,
 * and the stream and hook sinks are lambdas, so no real console is needed.
 */
class WindowsConsoleUtf8Test {
    @Test
    fun `redirected output is never touched`() {
        val codePage = RecordingCodePage(output = 850, input = 850)
        var streamsReplaced = false
        var restore: (() -> Unit)? = null

        val changed = WindowsConsoleUtf8.apply(
            terminal = false,
            isWindows = true,
            codePage = codePage,
            streamEncoding = { Charsets.UTF_8 }, // even a stream that is not UTF-8 is left alone
            replaceStreams = { streamsReplaced = true },
            registerRestore = { restore = it },
        )

        assertFalse(changed)
        assertEquals(emptyList(), codePage.calls, "redirected output must not even be read")
        assertFalse(streamsReplaced)
        assertEquals(null, restore)
    }

    @Test
    fun `a non-Windows platform is never touched`() {
        val codePage = RecordingCodePage(output = 850, input = 850)
        var streamsReplaced = false
        var restore: (() -> Unit)? = null

        val changed = WindowsConsoleUtf8.apply(
            terminal = true,
            isWindows = false,
            codePage = codePage,
            streamEncoding = { Charsets.ISO_8859_1 },
            replaceStreams = { streamsReplaced = true },
            registerRestore = { restore = it },
        )

        assertFalse(changed)
        assertEquals(emptyList(), codePage.calls, "another platform must not even be read")
        assertFalse(streamsReplaced)
        assertEquals(null, restore)
    }

    @Test
    fun `an already-correct console writes nothing at all`() {
        val codePage = RecordingCodePage(
            output = WindowsConsoleUtf8.UTF8_CODE_PAGE,
            input = WindowsConsoleUtf8.UTF8_CODE_PAGE,
        )
        var streamsReplaced = false
        var restore: (() -> Unit)? = null

        val changed = WindowsConsoleUtf8.apply(
            terminal = true,
            isWindows = true,
            codePage = codePage,
            streamEncoding = { Charsets.UTF_8 },
            replaceStreams = { streamsReplaced = true },
            registerRestore = { restore = it },
        )

        assertFalse(changed)
        assertEquals(emptyList(), codePage.writes, "a correct console must not be written to")
        assertFalse(streamsReplaced)
        assertEquals(null, restore)
    }

    @Test
    fun `a UTF-8 code page with a non-UTF-8 stream still replaces only the stream`() {
        val codePage = RecordingCodePage(
            output = WindowsConsoleUtf8.UTF8_CODE_PAGE,
            input = WindowsConsoleUtf8.UTF8_CODE_PAGE,
        )
        var streamsReplaced = false
        var restore: (() -> Unit)? = null

        val changed = WindowsConsoleUtf8.apply(
            terminal = true,
            isWindows = true,
            codePage = codePage,
            streamEncoding = { Charsets.ISO_8859_1 },
            replaceStreams = { streamsReplaced = true },
            registerRestore = { restore = it },
        )

        assertTrue(changed)
        assertEquals(emptyList(), codePage.writes, "the code page is already right and must not be set")
        assertTrue(streamsReplaced, "a non-UTF-8 stream is mojibake on a UTF-8 console and must be replaced")
        assertEquals(null, restore, "nothing was changed on the console, so there is nothing to restore")
    }

    @Test
    fun `a console whose FFM binding is unavailable is left alone`() {
        val codePage = UnavailableCodePage()
        var streamsReplaced = false
        var restore: (() -> Unit)? = null

        val changed = WindowsConsoleUtf8.apply(
            terminal = true,
            isWindows = true,
            codePage = codePage,
            streamEncoding = { Charsets.UTF_8 },
            replaceStreams = { streamsReplaced = true },
            registerRestore = { restore = it },
        )

        assertFalse(changed, "without the native binding there is no way to make both halves agree")
        assertFalse(streamsReplaced, "replacing only the stream would produce mojibake")
        assertEquals(null, restore)
    }

    @Test
    fun `a warranted fix sets both code pages and pairs a restore with them`() {
        val codePage = RecordingCodePage(output = 850, input = 850)
        var streamsReplaced = false
        var restore: (() -> Unit)? = null

        val changed = WindowsConsoleUtf8.apply(
            terminal = true,
            isWindows = true,
            codePage = codePage,
            streamEncoding = { Charsets.ISO_8859_1 },
            replaceStreams = { streamsReplaced = true },
            registerRestore = { restore = it },
        )

        assertTrue(changed)
        assertEquals(
            listOf("setOutput(${WindowsConsoleUtf8.UTF8_CODE_PAGE})", "setInput(${WindowsConsoleUtf8.UTF8_CODE_PAGE})"),
            codePage.writes,
        )
        assertTrue(streamsReplaced)
        assertTrue(restore != null, "a console change must always pair with a restore")

        // The restore puts back the values that were read, not an assumed default.
        restore!!.invoke()
        assertEquals(
            listOf("setOutput(65001)", "setInput(65001)", "setOutput(850)", "setInput(850)"),
            codePage.writes,
        )
    }

    @Test
    fun `the restore returns the code page the console actually had`() {
        val codePage = RecordingCodePage(output = 1252, input = 437)
        var restore: (() -> Unit)? = null

        WindowsConsoleUtf8.apply(
            terminal = true,
            isWindows = true,
            codePage = codePage,
            streamEncoding = { Charsets.ISO_8859_1 },
            replaceStreams = {},
            registerRestore = { restore = it },
        )
        restore!!.invoke()

        assertEquals(
            listOf("setOutput(65001)", "setInput(65001)", "setOutput(1252)", "setInput(437)"),
            codePage.writes,
            "1252 and 437 must be restored, not a hardcoded 850",
        )
    }

    /** A [ConsoleCodePage] that records its calls and never fails. */
    private class RecordingCodePage(private val output: Int, private val input: Int) : ConsoleCodePage {
        private val recorded = mutableListOf<String>()

        override val isAvailable: Boolean = true

        /** Every call made on this fake, in order. */
        val calls: List<String> get() = recorded

        /** Only the calls that mutate the console, which a no-op must never make. */
        val writes: List<String> get() = recorded.filter { it.startsWith("set") }

        override fun output(): Int = output.also { recorded += "output" }

        override fun input(): Int = input.also { recorded += "input" }

        override fun setOutput(codePage: Int): Boolean {
            recorded += "setOutput($codePage)"
            return true
        }

        override fun setInput(codePage: Int): Boolean {
            recorded += "setInput($codePage)"
            return true
        }
    }

    /** A code page whose native binding never resolved, such as a JVM that forbids native access. */
    private class UnavailableCodePage : ConsoleCodePage {
        override val isAvailable: Boolean = false

        override fun output(): Int = error("must not be read")

        override fun input(): Int = error("must not be read")

        override fun setOutput(codePage: Int): Boolean = error("must not be written")

        override fun setInput(codePage: Int): Boolean = error("must not be written")
    }
}
