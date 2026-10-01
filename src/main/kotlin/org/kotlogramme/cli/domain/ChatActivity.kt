package org.kotlogramme.cli.domain

/**
 * A chat status the account can report to a conversation.
 *
 * The names are the CLI spellings; the adapter maps each to the facade's own action. Only the
 * statuses a person would actually send are listed — the layer's `cancel` and `historyImport` are
 * machinery, not a status a terminal user types.
 */
enum class ChatActivity(val cliName: String) {
    TYPING("typing"),
    UPLOAD_PHOTO("upload-photo"),
    UPLOAD_DOCUMENT("upload-document"),
    RECORD_VIDEO("record-video"),
    UPLOAD_VIDEO("upload-video"),
    RECORD_VOICE("record-voice"),
    UPLOAD_VOICE("upload-voice"),
    RECORD_VIDEO_NOTE("record-video-note"),
    UPLOAD_VIDEO_NOTE("upload-video-note"),
    CHOOSE_STICKER("choose-sticker"),
    CHOOSE_CONTACT("choose-contact"),
    GEO_LOCATION("geo-location"),
    GAME_PLAY("game-play"),
    SPEAKING_IN_GROUP_CALL("speaking-in-group-call"),
}
