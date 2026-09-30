package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.InlineQuery

/** The columns of the inline-result list. */
internal val INLINE_RESULT_HEADERS = listOf("index", "id", "type", "title", "description", "text")

/** Prints an inline answer's numbered results, which is what `--send` names one by. */
fun Output.renderInlineResults(query: InlineQuery) {
    table(INLINE_RESULT_HEADERS, inlineResultRows(query))
}

/** One row per result: its index in the answer, its id and what the bot described. */
internal fun inlineResultRows(query: InlineQuery): List<List<String>> =
    query.results.mapIndexed { index, result ->
        listOf(
            index.toString(),
            result.id,
            result.type,
            result.title.orEmpty(),
            result.description.orEmpty(),
            result.text.orEmpty(),
        )
    }
