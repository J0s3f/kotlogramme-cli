package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatPermissions as FacadePermissions
import com.github.badoualy.telegram.api.ChatRestrictions as FacadeRestrictions
import com.github.badoualy.telegram.api.ParticipantPermissions as FacadeParticipantPermissions
import org.kotlogramme.cli.domain.ChatRestrictions as DomainRestrictions
import org.kotlogramme.cli.domain.ChatRights
import java.time.Instant

/** The facade spells "the restriction never lifts" as the epoch itself. */
internal const val NEVER_EXPIRES = 0L

/** The rights a promotion grants, as the facade's ten-flag `ChatPermissions`. */
internal fun ChatRights.toFacadePermissions(): FacadePermissions = FacadePermissions(
    changeInfo = changeInfo,
    postMessages = postMessages,
    editMessages = editMessages,
    deleteMessages = deleteMessages,
    banUsers = banUsers,
    inviteUsers = inviteUsers,
    pinMessages = pinMessages,
    addAdmins = addAdmins,
    anonymous = anonymous,
    manageCall = manageCall,
)

/** The facade's ten-flag `ChatPermissions`, read back as the domain rights. */
internal fun FacadePermissions.toChatRights(): ChatRights = ChatRights(
    changeInfo = changeInfo,
    postMessages = postMessages,
    editMessages = editMessages,
    deleteMessages = deleteMessages,
    banUsers = banUsers,
    inviteUsers = inviteUsers,
    pinMessages = pinMessages,
    addAdmins = addAdmins,
    anonymous = anonymous,
    manageCall = manageCall,
)

/**
 * Projects the role membership the facade's participant-permissions check answers.
 *
 * The check reports what the member *is* (creator, admin, banned, ordinary), not the granular set
 * of rights an admin holds, so a creator or an admin is shown as holding every right and an
 * ordinary or banned member as holding none. The one granular flag the check carries, [addAdmins],
 * is taken verbatim.
 */
internal fun FacadeParticipantPermissions.toChatRights(): ChatRights = when {
    isCreator -> ChatRights.ALL
    isAdmin -> ChatRights.ALL.copy(addAdmins = canAddAdmins)
    else -> ChatRights.NONE
}

/** The allowed abilities a restriction keeps, as the facade's flagged `ChatRestrictions`. */
internal fun DomainRestrictions.toFacadeRestrictions(): FacadeRestrictions = FacadeRestrictions(
    viewMessages = viewMessages,
    sendMessages = sendMessages,
    sendMedia = sendMedia,
    sendStickers = sendStickers,
    sendGifs = sendGifs,
    sendGames = sendGames,
    sendInline = sendInline,
    embedLinks = embedLinks,
    sendPolls = sendPolls,
    changeInfo = changeInfo,
    inviteUsers = inviteUsers,
    pinMessages = pinMessages,
    untilDate = untilDate?.toEpochMilli() ?: NEVER_EXPIRES,
)

/** The facade's flagged `ChatRestrictions`, read back as the domain restrictions. */
internal fun FacadeRestrictions.toDomainRestrictions(): DomainRestrictions = DomainRestrictions(
    viewMessages = viewMessages,
    sendMessages = sendMessages,
    sendMedia = sendMedia,
    sendStickers = sendStickers,
    sendGifs = sendGifs,
    sendGames = sendGames,
    sendInline = sendInline,
    embedLinks = embedLinks,
    sendPolls = sendPolls,
    changeInfo = changeInfo,
    inviteUsers = inviteUsers,
    pinMessages = pinMessages,
    untilDate = untilDate.takeIf { it != NEVER_EXPIRES }?.let { Instant.ofEpochMilli(it) },
)
