package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.InlineQueryResults
import com.github.badoualy.telegram.api.InlineResult as FacadeInlineResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InlineMapperTest {
    @Test
    fun `maps a text result with its title and posted text`() {
        val answer = InlineQueryResults(
            queryId = 99,
            results = listOf(
                FacadeInlineResult(
                    kind = "result",
                    id = "r1",
                    type = "article",
                    title = "Hello",
                    description = "says hello",
                    sendMessageText = "hello world",
                ),
            ),
        )

        val query = answer.toInlineQuery()

        assertEquals(99, query.queryId)
        val result = query.results.single()
        assertEquals("r1", result.id)
        assertEquals("article", result.type)
        assertEquals("Hello", result.title)
        assertEquals("says hello", result.description)
        assertEquals("hello world", result.text)
    }

    @Test
    fun `maps a media result without a title or posted text`() {
        val answer = InlineQueryResults(
            queryId = 7,
            results = listOf(
                FacadeInlineResult(
                    kind = "mediaResult",
                    id = "r2",
                    type = "photo",
                    documentId = 1234,
                ),
            ),
        )

        val result = answer.toInlineQuery().results.single()

        assertEquals("r2", result.id)
        assertEquals("photo", result.type)
        assertNull(result.title)
        assertNull(result.description)
        assertNull(result.text)
    }
}
