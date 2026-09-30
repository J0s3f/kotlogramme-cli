package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade full-text search calls the search gateway needs, narrowed to a seam a test can
 * implement.
 *
 * This exists so [KotlogramMessageSearchGateway] can be exercised without a live client. The
 * per-chat and global calls are separate methods, so the gateway chooses one without a flag.
 */
internal interface FacadeSearchOperations {
    /** Searches the text of messages in [peer], which is `messagesSearch`. */
    fun search(peer: TelegramPeer, query: String, limit: Int): List<Message>

    /** Counts the messages in [peer] that match [query], which is `messagesSearchTotal`. */
    fun total(peer: TelegramPeer, query: String): Int

    /** Searches the whole account for [query], which is `messagesSearchGlobal`. */
    fun searchGlobal(query: String, limit: Int): List<Message>

    /** Counts the messages a global search matches, which is `messagesSearchGlobalTotal`. */
    fun totalGlobal(query: String): Int
}
