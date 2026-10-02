package org.kotlogramme.cli.domain

/**
 * The kinds of file a chat listing can be restricted to.
 *
 * Telegram does the filtering, not this client: a listing asks the server for one media kind and gets
 * every message of that kind in the chat, however far back it is. That is why a listing cannot be a
 * history scan - a scan only reaches as far as it read.
 *
 * [filter] is the name that filter carries on the wire, the TL constructor without its
 * `inputMessagesFilter` prefix, and it is the contract with the facade. [cliName] is how a user
 * spells the kind.
 *
 * [ALL] is not a filter of its own: Telegram has no "every file" filter, so a listing for it is the
 * union of the other kinds, merged by the service before any filter is named. Its [filter] is
 * therefore empty and must never reach the gateway.
 *
 * [ANIMATION] deliberately shares the GIF filter with [GIF]: Telegram has no separate animation
 * filter, and its own clients list animations under the GIF one.
 */
enum class MediaFileKind(val cliName: String, val filter: String) {
    ALL("all", ""),
    PHOTOS("photo", "photos"),
    VIDEO("video", "video"),
    PHOTO_VIDEO("photo-video", "photoVideo"),
    DOCUMENT("document", "document"),
    MUSIC("audio", "music"),
    VOICE("voice", "voice"),
    GIF("gif", "gif"),
    ANIMATION("animation", "gif"),
}
