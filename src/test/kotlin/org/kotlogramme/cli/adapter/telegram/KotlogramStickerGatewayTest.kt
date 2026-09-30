package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AllStickers
import com.github.badoualy.telegram.api.StickerSetResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KotlogramStickerGatewayTest {
    @Test
    fun `sets asks for the installed sets and maps them in order`() {
        val operations = FakeStickerOperations().apply {
            all = allStickers(listOf(stickerSet(id = 1, shortName = "a"), stickerSet(id = 2, shortName = "b")))
        }

        val sets = KotlogramStickerGateway(operations).sets()

        assertEquals(1, operations.setListCalls)
        assertEquals(listOf("a", "b"), sets.map { it.shortName })
    }

    @Test
    fun `sets treats the not-modified marker as an empty list`() {
        val operations = FakeStickerOperations().apply { all = allStickers(emptyList(), notModified = true) }

        assertEquals(emptyList(), KotlogramStickerGateway(operations).sets())
    }

    @Test
    fun `set parses a short name into the short-name shape`() {
        val operations = FakeStickerOperations().apply { result = stickerSetResult(stickerSet(shortName = "cats")) }

        val set = KotlogramStickerGateway(operations).set("cats")

        assertEquals(listOf(StickerCall(id = null, accessHash = null, shortName = "cats")), operations.setCalls)
        assertEquals("cats", set.shortName)
    }

    @Test
    fun `set parses id and access hash into the id shape`() {
        val operations = FakeStickerOperations().apply { result = stickerSetResult(stickerSet()) }

        val set = KotlogramStickerGateway(operations).set("42:99")

        assertEquals(listOf(StickerCall(id = 42, accessHash = 99, shortName = null)), operations.setCalls)
        assertEquals(42L, set.id)
    }

    @Test
    fun `set rejects a malformed id reference before the facade`() {
        val operations = FakeStickerOperations()

        assertFailsWith<IllegalArgumentException> { KotlogramStickerGateway(operations).set("abc:def") }
        assertEquals(emptyList(), operations.setCalls)
    }

    @Test
    fun `set raises a clear error for the not-modified marker`() {
        val operations = FakeStickerOperations().apply { result = stickerSetResult(set = null, notModified = true) }

        assertFailsWith<IllegalStateException> { KotlogramStickerGateway(operations).set("cats") }
    }
}

internal data class StickerCall(val id: Long?, val accessHash: Long?, val shortName: String?)

internal class FakeStickerOperations : FacadeStickerOperations {
    var all: AllStickers = AllStickers(notModified = false, hash = 0, sets = emptyList())
    var result: StickerSetResult = StickerSetResult(notModified = false, set = null)
    var setListCalls = 0
    val setCalls = mutableListOf<StickerCall>()

    override fun sets(): AllStickers {
        setListCalls += 1
        return all
    }

    override fun set(id: Long?, accessHash: Long?, shortName: String?): StickerSetResult {
        setCalls += StickerCall(id, accessHash, shortName)
        return result
    }
}
