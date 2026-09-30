package org.kotlogramme.cli.domain

import java.time.Instant

/**
 * What a restricted member may still do, mirroring the facade's `ChatRestrictions`, where a flag is
 * `true` when the member is *allowed* to do it.
 *
 * [untilDate] is when the restriction lifts, or `null` for forever. The companion values are the
 * two ends a CLI needs: [NONE_ALLOWED] is a full ban, [ALL_ALLOWED] is no restriction at all.
 */
data class ChatRestrictions(
    val viewMessages: Boolean = true,
    val sendMessages: Boolean = true,
    val sendMedia: Boolean = true,
    val sendStickers: Boolean = true,
    val sendGifs: Boolean = true,
    val sendGames: Boolean = true,
    val sendInline: Boolean = true,
    val embedLinks: Boolean = true,
    val sendPolls: Boolean = true,
    val changeInfo: Boolean = true,
    val inviteUsers: Boolean = true,
    val pinMessages: Boolean = true,
    val untilDate: Instant? = null,
) {
    companion object {
        val NONE_ALLOWED: ChatRestrictions = ChatRestrictions(
            viewMessages = false,
            sendMessages = false,
            sendMedia = false,
            sendStickers = false,
            sendGifs = false,
            sendGames = false,
            sendInline = false,
            embedLinks = false,
            sendPolls = false,
            changeInfo = false,
            inviteUsers = false,
            pinMessages = false,
        )

        val ALL_ALLOWED: ChatRestrictions = ChatRestrictions()
    }
}
