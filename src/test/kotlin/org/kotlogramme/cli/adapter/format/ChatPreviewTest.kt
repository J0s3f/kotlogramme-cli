package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatPreviewTest {
    @Test
    fun `a short preview is kept as it is`() {
        assertEquals("hello", previewOf("hello"))
    }

    @Test
    fun `a null preview becomes an empty cell`() {
        assertEquals("", previewOf(null))
    }

    @Test
    fun `newlines are collapsed into single spaces`() {
        assertEquals("first line second line", previewOf("first line\n\nsecond line"))
    }

    @Test
    fun `a long preview is cut to the limit`() {
        val preview = previewOf("x".repeat(500))

        assertEquals(PREVIEW_LIMIT, preview.length)
        assertEquals("…", preview.last().toString())
    }

    @Test
    fun `cutting never leaves a trailing space before the ellipsis`() {
        val preview = previewOf("a".repeat(PREVIEW_LIMIT - 1) + " tail")

        assertEquals("a".repeat(PREVIEW_LIMIT - 1) + "…", preview)
    }
}
