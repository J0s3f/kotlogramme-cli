package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.ApiCredentials
import java.nio.file.Path

/** Checks that the facade's native library can be loaded on this machine. */
fun interface NativeLibraryProbe {
    fun check(credentials: ApiCredentials, sessionPath: Path): NativeLibraryCheck
}

/** What [NativeLibraryProbe] found. */
sealed interface NativeLibraryCheck {
    /** The facade client was built — which loads the native library — and then closed without a network query. */
    data object Loaded : NativeLibraryCheck

    /** The native library could not be loaded; [reason] explains why in user terms. */
    data class Unavailable(val reason: String) : NativeLibraryCheck
}

/**
 * Loads the native library by building the real facade client through [TelegramClientFactory].
 *
 * The facade extracts `native/<platform>/...` from its own classpath and `System.load`s it, so a
 * successful construction is the proof that the library is present and loadable. A [LinkageError]
 * (the family of `UnsatisfiedLinkError`) is translated into [NativeLibraryCheck.Unavailable] so the
 * command can explain the problem instead of printing a stack trace.
 */
internal class FacadeNativeLibraryProbe(
    private val factory: TelegramClientFactory = TelegramClientFactory(),
) : NativeLibraryProbe {
    override fun check(credentials: ApiCredentials, sessionPath: Path): NativeLibraryCheck = try {
        val client = factory.create(credentials, sessionPath)
        try {
            NativeLibraryCheck.Loaded
        } finally {
            client.close()
        }
    } catch (error: LinkageError) {
        NativeLibraryCheck.Unavailable(describe(error))
    }

    private fun describe(error: Throwable): String =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull()
            ?: error::class.qualifiedName
            ?: error.toString()
}
