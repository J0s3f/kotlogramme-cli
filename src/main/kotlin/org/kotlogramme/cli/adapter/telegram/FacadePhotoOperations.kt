package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.ProfilePhoto
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade photo calls the photo gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramPhotoGateway] can be exercised without a live client. `profilePhotos`
 * delegates to the facade's `getProfilePhotos`, which iterates the underlying `iterProfilePhotos`.
 */
internal interface FacadePhotoOperations {
    /** Lists the messages of [peer] that carry a chat photo, which is `messagesGetChatPhotos`. */
    fun chatPhotos(peer: TelegramPeer, limit: Int): List<Message>

    /** Lists up to [limit] profile photos of [peer], which is `getProfilePhotos`. */
    fun profilePhotos(peer: TelegramPeer, limit: Int): List<ProfilePhoto>
}

/** The real [FacadePhotoOperations], delegating straight to the facade client. */
internal class KotlogramPhotoOperations(private val client: TelegramClient) : FacadePhotoOperations {
    override fun chatPhotos(peer: TelegramPeer, limit: Int): List<Message> =
        client.messagesGetChatPhotos(peer, limit = limit)

    override fun profilePhotos(peer: TelegramPeer, limit: Int): List<ProfilePhoto> =
        client.getProfilePhotos(peer, limit)
}
