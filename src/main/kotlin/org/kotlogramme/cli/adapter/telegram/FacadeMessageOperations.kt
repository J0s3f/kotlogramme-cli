package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade message call the message gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramMessageGateway] can be exercised without a live client. The facade's
 * `offsetId` is the [beforeMessageId] the port names.
 */
internal interface FacadeMessageOperations {
    /** The newest messages of [peer] before [beforeMessageId], which is `messagesGetHistory`. */
    fun history(peer: TelegramPeer, limit: Int, beforeMessageId: Int?): List<Message>
}
