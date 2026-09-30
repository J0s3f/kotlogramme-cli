package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.StickerGateway
import org.kotlogramme.cli.domain.StickerSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StickerServiceTest {
    @Test
    fun `sets reads through the gateway`() {
        val gateway = FakeStickerGateway().apply { stored = listOf(aSet) }

        val result = StickerService(gateway).sets()

        assertEquals(1, gateway.setListCalls)
        assertEquals(listOf(aSet), result)
    }

    @Test
    fun `set passes the reference through`() {
        val gateway = FakeStickerGateway().apply { single = aSet }

        val result = StickerService(gateway).set("cats")

        assertEquals(listOf("cats"), gateway.references)
        assertEquals(aSet, result)
    }

    @Test
    fun `rejects a blank reference before the gateway`() {
        val gateway = FakeStickerGateway()
        val service = StickerService(gateway)

        assertFailsWith<IllegalArgumentException> { service.set("") }
        assertFailsWith<IllegalArgumentException> { service.set("   ") }
        assertEquals(emptyList(), gateway.references)
    }

    private class FakeStickerGateway : StickerGateway {
        var stored: List<StickerSet> = emptyList()
        var single: StickerSet? = null
        var setListCalls = 0
        val references = mutableListOf<String>()

        override fun sets(): List<StickerSet> {
            setListCalls += 1
            return stored
        }

        override fun set(reference: String): StickerSet {
            references += reference
            return single ?: error("no set configured")
        }
    }

    private companion object {
        val aSet = StickerSet(id = 42, accessHash = 99, title = "Cats", shortName = "cats", count = 1)
    }
}
