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
    /**
     * Lists the messages of [peer] that record a change to the chat's photo, which is
     * `messagesGetChatPhotos`.
     *
     * [offsetId] continues from a previous page; [all] walks the whole set in one call and wins over
     * it and [limit].
     */
    fun chatPhotos(peer: TelegramPeer, limit: Int, offsetId: Int? = null, all: Boolean = false): List<Message>

    /**
     * Lists up to [limit] profile photos of [peer], which is `getProfilePhotos`.
     *
     * [offset] continues from a previous page; [all] walks the whole set in one call and wins over it
     * and [limit].
     */
    fun profilePhotos(peer: TelegramPeer, limit: Int, offset: Int? = null, all: Boolean = false): List<ProfilePhoto>
}

/** The real [FacadePhotoOperations], delegating straight to the facade client. */
internal class KotlogramPhotoOperations(private val client: TelegramClient) : FacadePhotoOperations {
    override fun chatPhotos(peer: TelegramPeer, limit: Int, offsetId: Int?, all: Boolean): List<Message> =
        client.messagesGetChatPhotos(peer, limit = limit, offsetId = offsetId, all = all)

    override fun profilePhotos(peer: TelegramPeer, limit: Int, offset: Int?, all: Boolean): List<ProfilePhoto> =
        client.getProfilePhotos(peer, limit = limit, offset = offset, all = all)
}
