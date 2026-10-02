package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Photo
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCommandTest {
    @Test
    fun `chat-photo-history lists with the default limit`() {
        val fake = FakePhotos(chatPhotoMessages = listOf(testMessage))
        val fixture = cliFixture(photos = fake)

        val result = fixture.run("chat-photo-history", "@ada")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("@ada" to 50), fake.chatPhotoCalls)
        assertEquals(
            listOf(
                "id\tkind\tsize\tname\tduration\tdate",
                "7\t\t\t\t\t2026-01-01T12:30:00Z",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `profile-photos lists with the requested limit`() {
        val fake = FakePhotos(
            profile = listOf(Photo(id = 42, dcId = 2, sizeBytes = 1024, width = 320, height = 240, spoiler = false)),
        )
        val fixture = cliFixture(photos = fake)

        val result = fixture.run("profile-photos", "@ada", "--limit", "5")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("@ada" to 5), fake.profilePhotoCalls)
        assertEquals(
            listOf(
                "id\tdc\tsize\twidth\theight\tspoiler",
                "42\t2\t1024\t320\t240\t",
            ),
            fixture.output.lines,
        )
    }
}
