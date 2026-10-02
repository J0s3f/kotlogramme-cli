package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.MessageSearchFilter
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade full-text search calls the search gateway needs, narrowed to a seam a test can
 * implement.
 *
 * This exists so [KotlogramMessageSearchGateway] can be exercised without a live client. The
 * per-chat and global calls are separate methods, so the gateway chooses one without a flag. The
 * media-kind listing is a search with no query text and a filter, which is why it is its own call
 * here rather than a flag on [search].
 */
internal interface FacadeSearchOperations {
    /**
     * Searches the text of messages in [peer], which is `messagesSearch`.
     *
     * [offsetId] continues from a previous page, as the cursor the command printed names it.
     */
    fun search(peer: TelegramPeer, query: String, limit: Int, offsetId: Int? = null): List<Message>

    /** Counts the messages in [peer] that match [query], which is `messagesSearchTotal`. */
    fun total(peer: TelegramPeer, query: String): Int

    /**
     * Searches the whole account for [query], which is `messagesSearchGlobal`.
     *
     * [offsetId] continues from a previous page, as the cursor the command printed names it.
     */
    fun searchGlobal(query: String, limit: Int, offsetId: Int? = null): List<Message>

    /** Counts the messages a global search matches, which is `messagesSearchGlobalTotal`. */
    fun totalGlobal(query: String): Int

    /**
     * Lists the messages of [peer] carrying media of [filter], which is `messagesSearch` filtered.
     *
     * [offsetId] continues from a previous page, as the cursor the command printed names it.
     */
    fun searchFiles(peer: TelegramPeer, filter: MessageSearchFilter, limit: Int, offsetId: Int? = null): List<Message>

    /** Counts the messages of [peer] carrying media of [filter], which is `messagesSearchTotal`. */
    fun totalFiles(peer: TelegramPeer, filter: MessageSearchFilter): Int
}
