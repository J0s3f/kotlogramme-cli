package org.kotlogramme.cli.adapter.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StickerMapperTest {
    @Test
    fun `maps the identity, the count and every flag`() {
        val set = stickerSet(
            count = 3,
            archived = true,
            official = true,
            masks = true,
            emojis = true,
        ).toStickerSet()

        assertEquals(42L, set.id)
        assertEquals(99L, set.accessHash)
        assertEquals("Cats", set.title)
        assertEquals("cats", set.shortName)
        assertEquals(3, set.count)
        assertTrue(set.archived)
        assertTrue(set.official)
        assertTrue(set.masks)
        assertTrue(set.emojis)
    }

    @Test
    fun `keeps the flags off when the facade has them off`() {
        val set = stickerSet().toStickerSet()

        assertFalse(set.archived)
        assertFalse(set.official)
        assertFalse(set.masks)
        assertFalse(set.emojis)
    }

    @Test
    fun `projects each pack's emoticon and its document ids`() {
        val set = stickerSet(
            packs = listOf(
                stickerPack("🐱", listOf(10L, 11L)),
                stickerPack("😺", listOf(12L)),
            ),
        ).toStickerSet()

        assertEquals(listOf("🐱", "😺"), set.packs.map { it.emoticon })
        assertEquals(listOf(listOf(10L, 11L), listOf(12L)), set.packs.map { it.documentIds })
        assertEquals(3, set.projectedCount)
    }

    @Test
    fun `an empty projection counts zero`() {
        assertEquals(0, stickerSet().toStickerSet().projectedCount)
    }
}
