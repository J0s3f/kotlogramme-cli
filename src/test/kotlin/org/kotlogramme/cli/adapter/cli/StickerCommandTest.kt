package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.api.Stickers
import org.kotlogramme.cli.domain.StickerSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StickerCommandTest {
    @Test
    fun `stickers lists the account's sets`() {
        val fixture = cliFixture(stickers = FakeStickers(sets = listOf(testStickerSet())))

        val result = fixture.run("stickers")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("cats	Cats	2"), fixture.output.text)
    }

    @Test
    fun `sticker-set shows the set with its stickers numbered`() {
        val fake = FakeStickers(setAnswer = testStickerSet())
        val fixture = cliFixture(stickers = fake)

        val result = fixture.run("sticker-set", "cats")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("cats"), fake.setRequests)
        assertTrue(fixture.output.text.contains("Cats (cats)"), fixture.output.text)
        assertTrue(fixture.output.text.contains("1	22	"), fixture.output.text)
    }

    @Test
    fun `send-sticker sends the numbered sticker and prints the message`() {
        val fake = FakeStickers()
        val fixture = cliFixture(stickers = fake)

        val result = fixture.run("send-sticker", "@ada", "cats", "1", "--reply-to", "9", "--silent")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(StickerSendCall("@ada", "cats", 1, 9, true)), fake.sends)
        assertTrue(fixture.output.text.contains("hello"), fixture.output.text)
    }

    @Test
    fun `send-sticker without options is not silent and not a reply`() {
        val fake = FakeStickers()

        cliFixture(stickers = fake).run("send-sticker", "@ada", "cats", "0")

        assertEquals(listOf(StickerSendCall("@ada", "cats", 0, null, false)), fake.sends)
    }

    @Test
    fun `send-sticker needs a numeric index`() {
        val fake = FakeStickers()

        val result = cliFixture(stickers = fake).run("send-sticker", "@ada", "cats", "first")

        assertNotEquals(0, result.statusCode)
        assertTrue(fake.sends.isEmpty())
    }

    @Test
    fun `a set Telegram does not know is a usage error`() {
        val unknown = object : Stickers by FakeStickers() {
            override fun set(reference: String): StickerSet = throw IllegalArgumentException("No sticker set '$reference'.")
        }

        val result = cliFixture(stickers = unknown).run("sticker-set", "nope")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("No sticker set 'nope'."), result.stderr)
    }
}
