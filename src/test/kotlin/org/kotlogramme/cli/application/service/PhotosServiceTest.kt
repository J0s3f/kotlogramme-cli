package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.PhotoGateway
import org.kotlogramme.cli.domain.Message
import org.kotlogramme.cli.domain.Photo
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotosServiceTest {
    @Test
    fun `chatPhotos reads through the gateway with the limit`() {
        val gateway = FakePhotoGateway().apply { chats = listOf(chatPhoto) }

        val result = PhotosService(gateway).chatPhotos("@ada", limit = 20)

        assertEquals(listOf("@ada" to 20), gateway.chatCalls)
        assertEquals(listOf(chatPhoto), result)
    }

    @Test
    fun `profilePhotos reads through the gateway with the limit`() {
        val gateway = FakePhotoGateway().apply { profile = listOf(profilePhoto) }

        val result = PhotosService(gateway).profilePhotos("@ada", limit = 5)

        assertEquals(listOf("@ada" to 5), gateway.profileCalls)
        assertEquals(listOf(profilePhoto), result)
    }

    @Test
    fun `rejects a non-positive limit before the gateway`() {
        val gateway = FakePhotoGateway()
        val service = PhotosService(gateway)

        assertFailsWith<IllegalArgumentException> { service.chatPhotos("@ada", 0) }
        assertFailsWith<IllegalArgumentException> { service.profilePhotos("@ada", -1) }
        assertEquals(emptyList(), gateway.chatCalls)
        assertEquals(emptyList(), gateway.profileCalls)
    }

    private class FakePhotoGateway : PhotoGateway {
        var chats: List<Message> = emptyList()
        var profile: List<Photo> = emptyList()
        val chatCalls = mutableListOf<Pair<String, Int>>()
        val profileCalls = mutableListOf<Pair<String, Int>>()

        override fun chatPhotos(reference: String, limit: Int): List<Message> {
            chatCalls += reference to limit
            return chats
        }

        override fun profilePhotos(reference: String, limit: Int): List<Photo> {
            profileCalls += reference to limit
            return profile
        }
    }

    private companion object {
        val chatPhoto = Message(
            id = 7,
            senderName = "Ada",
            text = "",
            sentAt = Instant.parse("2026-01-01T12:30:00Z"),
            outgoing = false,
        )
        val profilePhoto = Photo(id = 42, dcId = 2, sizeBytes = 1024, width = 320, height = 240, spoiler = false)
    }
}
