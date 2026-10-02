package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Photo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `chat-photo-history passes the cursor and all to the use case`() {
        val fake = FakePhotos(chatPhotoMessages = listOf(testMessage))
        val fixture = cliFixture(photos = fake)

        fixture.run("chat-photo-history", "@ada", "--after", "99", "--all")

        assertEquals(listOf<String?>("99"), fake.chatPhotoCursors)
        assertEquals(listOf(true), fake.chatPhotoAlls)
    }

    @Test
    fun `chat-photo-history prints the next cursor on a full page`() {
        val messages = (1..50).map { index -> testMessage.copy(id = index) }
        val fake = FakePhotos(chatPhotoMessages = messages)
        val fixture = cliFixture(photos = fake)

        val result = fixture.run("chat-photo-history", "@ada")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 50"), fixture.output.text)
    }

    @Test
    fun `chat-photo-history omits the next cursor on a short page`() {
        val fake = FakePhotos(chatPhotoMessages = listOf(testMessage))
        val fixture = cliFixture(photos = fake)

        fixture.run("chat-photo-history", "@ada")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `chat-photo-history rejects a malformed cursor`() {
        val fake = FakePhotos(chatPhotoMessages = listOf(testMessage))
        val fixture = cliFixture(photos = fake)

        val result = fixture.run("chat-photo-history", "@ada", "--after", "abc")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed cursor"), "stderr was: ${result.stderr}")
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

    @Test
    fun `profile-photos passes the cursor and all to the use case`() {
        val fake = FakePhotos(profile = emptyList())
        val fixture = cliFixture(photos = fake)

        fixture.run("profile-photos", "@ada", "--after", "10", "--all")

        assertEquals(listOf<String?>("10"), fake.profilePhotoCursors)
        assertEquals(listOf(true), fake.profilePhotoAlls)
    }

    @Test
    fun `profile-photos prints the next cursor on a full page`() {
        val photos = (1..50).map { index ->
            Photo(id = index.toLong(), dcId = 2, sizeBytes = 1024, width = 320, height = 240, spoiler = false)
        }
        val fake = FakePhotos(profile = photos)
        val fixture = cliFixture(photos = fake)

        val result = fixture.run("profile-photos", "@ada")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 50"), fixture.output.text)
    }

    @Test
    fun `profile-photos omits the next cursor on a short page`() {
        val fake = FakePhotos(
            profile = listOf(Photo(id = 42, dcId = 2, sizeBytes = 1024, width = 320, height = 240, spoiler = false)),
        )
        val fixture = cliFixture(photos = fake)

        fixture.run("profile-photos", "@ada")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `profile-photos rejects a malformed cursor`() {
        val fake = FakePhotos(profile = emptyList())
        val fixture = cliFixture(photos = fake)

        val result = fixture.run("profile-photos", "@ada", "--after", "abc")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed cursor"), "stderr was: ${result.stderr}")
    }
}
