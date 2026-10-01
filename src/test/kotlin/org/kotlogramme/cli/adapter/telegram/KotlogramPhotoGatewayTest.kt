package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.ProfilePhoto
import com.github.badoualy.telegram.api.TelegramPeer
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramPhotoGatewayTest {
    private val ada = peer(id = 7, kind = "user", username = "ada", name = "Ada")

    @Test
    fun `chatPhotos resolves the reference and maps the messages`() {
        val operations = FakePhotoOperations().apply {
            messages = listOf(message(id = 7, text = "", date = 1_000))
        }

        val photos = gatewayWith(operations).chatPhotos("@ada", limit = 10)

        assertEquals(listOf(ada to 10), operations.chatCalls)
        assertEquals(listOf(7), photos.map { it.id })
    }

    @Test
    fun `profilePhotos resolves the reference and maps the photos`() {
        val operations = FakePhotoOperations().apply {
            profile = listOf(ProfilePhoto(id = 42, dcId = 2, size = 1024, width = 320, height = 240))
        }

        val photos = gatewayWith(operations).profilePhotos("@ada", limit = 5)

        assertEquals(listOf(ada to 5), operations.profileCalls)
        assertEquals(42L, photos.single().id)
        assertEquals(2, photos.single().dcId)
        assertEquals(1024L, photos.single().sizeBytes)
        assertEquals(320, photos.single().width)
    }

    private fun gatewayWith(operations: FakePhotoOperations): KotlogramPhotoGateway =
        KotlogramPhotoGateway(
            operations,
            ChatReferenceResolver(FakeChatOperations().apply { resolvedPeer = ada }),
        )
}

private class FakePhotoOperations : FacadePhotoOperations {
    var messages: List<Message> = emptyList()
    var profile: List<ProfilePhoto> = emptyList()
    val chatCalls = mutableListOf<Pair<TelegramPeer, Int>>()
    val profileCalls = mutableListOf<Pair<TelegramPeer, Int>>()

    override fun chatPhotos(peer: TelegramPeer, limit: Int): List<Message> {
        chatCalls += peer to limit
        return messages
    }

    override fun profilePhotos(peer: TelegramPeer, limit: Int): List<ProfilePhoto> {
        profileCalls += peer to limit
        return profile
    }
}
