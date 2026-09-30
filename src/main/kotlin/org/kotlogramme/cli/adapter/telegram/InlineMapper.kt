package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.InlineQueryResults as FacadeInlineQueryResults
import com.github.badoualy.telegram.api.InlineResult as FacadeInlineResult
import org.kotlogramme.cli.domain.InlineQuery
import org.kotlogramme.cli.domain.InlineResult

/**
 * Reduces a facade inline answer to what a terminal client shows and sends.
 *
 * The facade carries the text a text result would post in `sendMessageText`, which is null for a
 * media-carrying result; that is exactly the domain's `text`, so a media result maps to a null text
 * rather than an empty one.
 */
internal fun FacadeInlineQueryResults.toInlineQuery(): InlineQuery = InlineQuery(
    queryId = queryId,
    results = results.map { it.toInlineResult() },
)

internal fun FacadeInlineResult.toInlineResult(): InlineResult = InlineResult(
    id = id,
    type = type,
    title = title,
    description = description,
    text = sendMessageText,
)
