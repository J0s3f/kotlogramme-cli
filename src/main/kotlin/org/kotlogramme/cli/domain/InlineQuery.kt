package org.kotlogramme.cli.domain

/** One result of an inline query, reduced to what a terminal can show and send. */
data class InlineResult(
    val id: String,
    val type: String,
    val title: String?,
    val description: String?,
    /** The text the result would post, or `null` when the result carries media. */
    val text: String?,
)

/**
 * The answer to an inline query.
 *
 * [queryId] is what a chosen result is sent with, so it must be carried from the query to the
 * send; Telegram expires it, which is why querying and sending are one client command.
 */
data class InlineQuery(
    val queryId: Long,
    val results: List<InlineResult>,
)
