package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Media as FacadeMedia
import com.github.badoualy.telegram.api.Message as FacadeMessage
import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Message
import java.time.Instant

/**
 * Reduces a facade message to what a terminal client renders.
 *
 * The facade reports [FacadeMessage.date] in epoch milliseconds (the native bridge converts the
 * wire's unix seconds with `Timestamp::as_millisecond`), so it is turned into an [Instant] with
 * [Instant.ofEpochMilli] and not `ofEpochSecond`.
 */
internal fun FacadeMessage.toMessage(): Message = Message(
    id = id,
    senderName = senderName(),
    text = text,
    sentAt = Instant.ofEpochMilli(date),
    outgoing = outgoing,
    edited = editDate != null,
    pinned = pinned,
    replyToMessageId = replyToMessageId,
    media = media?.toMediaInfo(),
    action = action?.kind?.let(::serviceAction),
)

private fun FacadeMedia.toMediaInfo(): MediaInfo = MediaInfo(
    kind = kind,
    durationSeconds = duration,
    width = width,
    height = height,
    sizeBytes = size,
    name = name,
)

private fun FacadeMessage.senderName(): String =
    sender?.displayName()
        ?: postAuthor?.takeIf(String::isNotBlank)
        ?: peer?.name?.takeIf(String::isNotBlank)
        ?: ""

internal fun User.displayName(): String = listOf(firstName.orEmpty(), lastName.orEmpty())
    .filter(String::isNotBlank)
    .joinToString(" ")
    .ifBlank { username ?: id.toString() }

/**
 * The service actions the native projection can emit, as phrases a person reads.
 *
 * The facade reports an action as the lowerCamelCase name of the layer variant it is, so this is a
 * lookup rather than a behaviour: each entry is the phrase that finishes "«sender» …" in the
 * history row. A kind missing here — a layer variant a later facade adds — is not dropped; it falls
 * back to naming the kind itself, so the row still says what happened instead of rendering blank.
 */
private val SERVICE_ACTIONS = mapOf(
    "empty" to "performed a service action",
    "chatCreate" to "created the group",
    "chatEditTitle" to "changed the group title",
    "chatEditPhoto" to "changed the group photo",
    "chatDeletePhoto" to "removed the group photo",
    "chatAddUser" to "added a member",
    "chatDeleteUser" to "removed a member",
    "chatJoinedByLink" to "joined via invite link",
    "channelCreate" to "created the channel",
    "chatMigrateTo" to "migrated the group to a supergroup",
    "channelMigrateFrom" to "migrated a group to this channel",
    "pinMessage" to "pinned a message",
    "historyClear" to "cleared the history",
    "gameScore" to "scored in a game",
    "paymentSentMe" to "sent you a payment",
    "paymentSent" to "sent a payment",
    "phoneCall" to "made a phone call",
    "screenshotTaken" to "took a screenshot",
    "customAction" to "performed a custom action",
    "botAllowed" to "granted a bot access",
    "secureValuesSentMe" to "sent you secure values",
    "secureValuesSent" to "sent secure values",
    "contactSignUp" to "joined Telegram",
    "geoProximityReached" to "reached a proximity alert",
    "groupCall" to "started a group call",
    "inviteToGroupCall" to "invited to a group call",
    "setMessagesTtl" to "set the message auto-delete timer",
    "groupCallScheduled" to "scheduled a group call",
    "setChatTheme" to "changed the chat theme",
    "chatJoinedByRequest" to "joined by request",
    "webViewDataSentMe" to "sent you web view data",
    "webViewDataSent" to "sent web view data",
    "giftPremium" to "gifted Telegram Premium",
    "topicCreate" to "created a topic",
    "topicEdit" to "edited a topic",
    "suggestProfilePhoto" to "suggested a profile photo",
    "requestedPeer" to "shared a chat",
    "setChatWallPaper" to "changed the chat wallpaper",
    "giftCode" to "gifted a subscription",
    "giveawayLaunch" to "launched a giveaway",
    "giveawayResults" to "completed a giveaway",
    "boostApply" to "boosted the channel",
    "requestedPeerSentMe" to "shared a chat with you",
    "paymentRefunded" to "refunded a payment",
    "giftStars" to "gifted stars",
    "prizeStars" to "awarded stars",
    "starGift" to "sent a star gift",
    "starGiftUnique" to "sent a unique star gift",
    "paidMessagesRefunded" to "refunded paid messages",
    "paidMessagesPrice" to "changed the paid-message price",
    "conferenceCall" to "started a conference call",
    "todoCompletions" to "completed todo items",
    "todoAppendTasks" to "added todo tasks",
    "suggestedPostApproval" to "approved a suggested post",
    "suggestedPostSuccess" to "published a suggested post",
    "suggestedPostRefund" to "refunded a suggested post",
    "giftTon" to "gifted Ton",
    "suggestBirthday" to "suggested a birthday",
)

/** Describes [kind], naming the kind itself when this build has no phrase for it. */
internal fun serviceAction(kind: String): String = SERVICE_ACTIONS[kind] ?: "service action: $kind"
