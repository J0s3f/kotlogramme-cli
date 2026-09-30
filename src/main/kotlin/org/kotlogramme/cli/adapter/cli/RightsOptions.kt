package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights
import java.time.Instant

/**
 * The right names the `promote` and `restrict` options accept.
 *
 * A name is the hyphenated form of the flag it sets, so `--grant post-messages` reads like the
 * right it grants. An unknown name is rejected with the accepted set rather than ignored.
 */
internal object RightsOptions {
    /** The ten admin rights `--grant` understands. */
    val ADMIN_NAMES = listOf(
        "change-info",
        "post-messages",
        "edit-messages",
        "delete-messages",
        "ban-users",
        "invite-users",
        "pin-messages",
        "add-admins",
        "anonymous",
        "manage-call",
    )

    /** The twelve abilities `--allow` understands, mirroring the restriction flags. */
    val RESTRICTION_NAMES = listOf(
        "view-messages",
        "send-messages",
        "send-media",
        "send-stickers",
        "send-gifs",
        "send-games",
        "send-inline",
        "embed-links",
        "send-polls",
        "change-info",
        "invite-users",
        "pin-messages",
    )

    /** Validates [names] against [known] and returns the trimmed selection as a set. */
    fun select(names: List<String>, known: List<String>): Set<String> {
        val selected = names.map(String::trim).filter(String::isNotEmpty)
        val unknown = selected.filterNot(known::contains)
        require(unknown.isEmpty()) {
            "unknown right ${unknown.joinToString(", ")}; expected one of ${known.joinToString(", ")}"
        }
        return selected.toSet()
    }

    /** The admin rights [selected] names, with everything else denied. */
    fun rights(selected: Set<String>): ChatRights = ChatRights(
        changeInfo = "change-info" in selected,
        postMessages = "post-messages" in selected,
        editMessages = "edit-messages" in selected,
        deleteMessages = "delete-messages" in selected,
        banUsers = "ban-users" in selected,
        inviteUsers = "invite-users" in selected,
        pinMessages = "pin-messages" in selected,
        addAdmins = "add-admins" in selected,
        anonymous = "anonymous" in selected,
        manageCall = "manage-call" in selected,
    )

    /** The abilities [selected] keeps, with everything else denied and the given [untilDate]. */
    fun restrictions(selected: Set<String>, untilDate: Instant?): ChatRestrictions = ChatRestrictions(
        viewMessages = "view-messages" in selected,
        sendMessages = "send-messages" in selected,
        sendMedia = "send-media" in selected,
        sendStickers = "send-stickers" in selected,
        sendGifs = "send-gifs" in selected,
        sendGames = "send-games" in selected,
        sendInline = "send-inline" in selected,
        embedLinks = "embed-links" in selected,
        sendPolls = "send-polls" in selected,
        changeInfo = "change-info" in selected,
        inviteUsers = "invite-users" in selected,
        pinMessages = "pin-messages" in selected,
        untilDate = untilDate,
    )
}
