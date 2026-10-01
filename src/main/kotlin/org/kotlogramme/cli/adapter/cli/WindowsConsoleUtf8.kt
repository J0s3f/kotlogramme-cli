package org.kotlogramme.cli.adapter.cli

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * The console code page, as the Windows API sees it.
 *
 * Separated from the decision of *whether* to change it so [WindowsConsoleUtf8.apply] can be
 * exercised with a fake, while the real implementation stays a leaf that only touches `kernel32`.
 */
internal interface ConsoleCodePage {
    /** True when this implementation can actually read and write the console. */
    val isAvailable: Boolean

    /** `GetConsoleOutputCP`, the code page the terminal decodes this process's bytes with. */
    fun output(): Int

    /** `GetConsoleCP`, the code page this process's input is decoded with. */
    fun input(): Int

    /** `SetConsoleOutputCP`; returns whether the console accepted the page. */
    fun setOutput(codePage: Int): Boolean

    /** `SetConsoleCP`; returns whether the console accepted the page. */
    fun setInput(codePage: Int): Boolean
}

/**
 * `kernel32`'s code-page calls through the FFM API.
 *
 * No native build and no JNA: `java.lang.foreign` binds the already-present `kernel32` directly. The
 * `libraryLookup` handle is a restricted method, so a run without `--enable-native-access` prints a
 * warning once; the application distribution passes that flag (see `build.gradle.kts`), and a future
 * JDK would block the call rather than merely warn, at which point the `orElseThrow` becomes a real
 * failure that this process tolerates by leaving the console alone (see [WindowsConsoleUtf8.apply]).
 *
 * The arena is global because the handles below live as long as the process; each is resolved once so
 * the hot path is four plain method invocations.
 */
internal object Kernel32ConsoleCodePage : ConsoleCodePage {
    private val handles: Handles? = runCatching { Handles() }.getOrNull()

    /** False when this JVM forbids native access, so no console change is attempted at all. */
    override val isAvailable: Boolean get() = handles != null

    // `invokeWithArguments` rather than `invokeExact`: Kotlin does not model a MethodHandle's
    // signature-polymorphic calls, so the exact form would emit a descriptor that does not match the
    // native function. These run a handful of times per process, so the boxing is irrelevant.
    override fun output(): Int =
        requireNotNull(handles) { "kernel32 symbols are unavailable" }.getOutput.invokeWithArguments() as Int

    override fun input(): Int =
        requireNotNull(handles) { "kernel32 symbols are unavailable" }.input.invokeWithArguments() as Int

    override fun setOutput(codePage: Int): Boolean =
        requireNotNull(handles) { "kernel32 symbols are unavailable" }.setOutput.invokeWithArguments(codePage) as Int ==
            SUCCESS

    override fun setInput(codePage: Int): Boolean =
        requireNotNull(handles) { "kernel32 symbols are unavailable" }.setInput.invokeWithArguments(codePage) as Int ==
            SUCCESS

    private class Handles {
        private val arena = Arena.ofShared()
        private val linker = Linker.nativeLinker()
        private val kernel32: SymbolLookup = SymbolLookup.libraryLookup(KERNEL32, arena)

        val getOutput: MethodHandle = downcall("GetConsoleOutputCP", FunctionDescriptor.of(ValueLayout.JAVA_INT))
        val input: MethodHandle = downcall("GetConsoleCP", FunctionDescriptor.of(ValueLayout.JAVA_INT))
        val setOutput: MethodHandle = downcall(
            "SetConsoleOutputCP",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        )
        val setInput: MethodHandle = downcall(
            "SetConsoleCP",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        )

        private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
            linker.downcallHandle(kernel32.find(name).orElseThrow { NoSuchElementException("kernel32!$name") }, descriptor)
    }

    private const val KERNEL32 = "kernel32"
    private const val SUCCESS = 1
}

/**
 * Makes this process's own stdout and stderr UTF-8 on a Windows console, once, and restores the
 * console's previous code page on exit.
 *
 * The interactive shell already renders through JLine's writer, which reaches the console with
 * `WriteConsoleW` and needs nothing here; this covers the one-shot commands, which write to
 * `System.out`. On a console whose output code page is not UTF-8 the JVM captured a non-UTF-8
 * encoder at startup, so both halves must change together: setting the code page alone leaves
 * `System.out` emitting CP850 bytes that the UTF-8 console decodes as mojibake, and changing the
 * encoder alone leaves the terminal decoding UTF-8 as CP850. The two checks are therefore
 * independent — a console already at 65001 can still have a non-UTF-8 stream (observed on JDK 25).
 */
internal object WindowsConsoleUtf8 {
    /** The Windows UTF-8 code page. */
    const val UTF8_CODE_PAGE = 65001

    /**
     * Applies the fix when — and only when — every condition holds:
     *
     * 1. the platform is Windows,
     * 2. stdout is a real console a person is watching ([terminal]),
     * 3. the console output code page is not already [UTF8_CODE_PAGE], or
     * 4. [System.out] does not already carry UTF-8.
     *
     * A run that is already correct performs **no** platform call, replaces **no** stream and
     * registers **no** hook. Redirected or piped output and every non-Windows platform are left
     * byte-identical to today.
     *
     * Returns true when anything was changed, so a caller could observe it; production ignores the
     * value. [replaceStreams] and [registerRestore] are injected so the decision can be tested
     * without a console: production passes [replaceStandardStreamsWithUtf8] and
     * [registerShutdownRestore].
     */
    fun apply(
        terminal: Boolean,
        isWindows: Boolean = isWindows(),
        codePage: ConsoleCodePage = Kernel32ConsoleCodePage,
        streamEncoding: () -> java.nio.charset.Charset = { System.out.charset() },
        replaceStreams: () -> Unit = ::replaceStandardStreamsWithUtf8,
        registerRestore: (() -> Unit) -> Unit = ::registerShutdownRestore,
    ): Boolean {
        if (!isWindows || !terminal || !codePage.isAvailable) return false

        val outputBefore = codePage.output()
        val inputBefore = codePage.input()
        val codePageNeedsFix = outputBefore != UTF8_CODE_PAGE
        val streamNeedsFix = streamEncoding() != Charsets.UTF_8
        if (!codePageNeedsFix && !streamNeedsFix) return false

        // Captured before the change, so the restore puts back exactly what this console had — 437,
        // 1252, 850, whatever it was — rather than an assumed default.
        if (codePageNeedsFix) {
            codePage.setOutput(UTF8_CODE_PAGE)
            codePage.setInput(UTF8_CODE_PAGE)
            registerRestore { restore(codePage, outputBefore, inputBefore) }
        }
        if (streamNeedsFix) replaceStreams()
        return true
    }

    private fun restore(codePage: ConsoleCodePage, outputBefore: Int, inputBefore: Int) {
        runCatching { codePage.setOutput(outputBefore) }
        runCatching { codePage.setInput(inputBefore) }
    }

    private fun registerShutdownRestore(restore: () -> Unit) {
        // A shutdown hook runs on a clean exit and on Ctrl-C, which is the last chance to put the
        // console back. A hard kill (TerminateProcess / taskkill /F) runs no hook at all and leaves
        // the console at 65001; nothing in-process can cover that case.
        runCatching { Runtime.getRuntime().addShutdownHook(Thread(restore, "console-codepage-restore")) }
    }

    /**
     * Replaces [System.out]/[System.err] with UTF-8 streams over the same file descriptors.
     *
     * This is the half that a code-page change cannot do: the JVM captured its encoder at startup
     * and does not follow a later `SetConsoleOutputCP`. `FileDescriptor.out` keeps the actual console
     * handle, so the UTF-8 bytes reach the same terminal the code page just described.
     */
    private fun replaceStandardStreamsWithUtf8() {
        System.setOut(utf8PrintStream(FileDescriptor.out))
        System.setErr(utf8PrintStream(FileDescriptor.err))
    }

    private fun utf8PrintStream(descriptor: FileDescriptor): PrintStream =
        PrintStream(FileOutputStream(descriptor), true, Charsets.UTF_8)

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)
}
