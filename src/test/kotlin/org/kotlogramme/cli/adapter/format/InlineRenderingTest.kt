package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.InlineResult
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineRenderingTest {
    private val answer = InlineQuery(
        queryId = 7L,
        results = listOf(
            InlineResult(id = "a", type = "article", title = "First", description = "one", text = "body"),
            InlineResult(id = "b", type = "photo", title = null, description = null, text = null),
        ),
    )

    @Test
    fun `each result becomes a row carrying its index and id`() {
        assertEquals(
            listOf(
                listOf("0", "a", "article", "First", "one", "body"),
                listOf("1", "b", "photo", "", "", ""),
            ),
            inlineResultRows(answer),
        )
    }
}
