package org.kotlogramme.cli.domain

import java.time.Instant

/**
 * One active session on the account.
 *
 * [hash] identifies the session for [org.kotlogramme.cli.application.port.api.Sessions.terminate];
 * it is only meaningful for the account it was listed from. [current] marks the session the listing
 * was made from, which is the one `terminate-all` keeps.
 */
data class Session(
    val hash: Long,
    val deviceModel: String,
    val platform: String,
    val appVersion: String,
    val ip: String,
    val country: String,
    val createdAt: Instant,
    val current: Boolean,
) {
    /** A readable device label, falling back to the platform when the device model is blank. */
    val device: String
        get() = deviceModel.takeIf(String::isNotBlank) ?: platform.takeIf(String::isNotBlank).orEmpty()
}
