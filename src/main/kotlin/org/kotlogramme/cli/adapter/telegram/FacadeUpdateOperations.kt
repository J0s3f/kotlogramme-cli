package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramUpdate

/**
 * The facade update calls the update source needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramUpdateSource] can be exercised without a live client. [next] returns
 * `null` when [timeoutMillis] elapses without an update; that is quiet, not the end of the stream.
 * [syncState] asks the facade to persist its update state without dropping the session.
 */
internal interface FacadeUpdateOperations {
    /** The next update, or `null` after [timeoutMillis]. */
    fun next(timeoutMillis: Long): TelegramUpdate?

    /** Asks the facade to write its update state again. */
    fun syncState()
}
