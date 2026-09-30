package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.UsageError

/**
 * Turns input the use case rejects into a usage error.
 *
 * A blank text or a non-positive message id is a mistake in the invocation, so it should read as a
 * one-line message with a non-zero exit code, never as a stack trace.
 */
internal inline fun <T> rejectInvalidInput(block: () -> T): T =
    try {
        block()
    } catch (error: IllegalArgumentException) {
        throw UsageError(error.message ?: "Invalid input.")
    }
