package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AccountAuthorization
import org.kotlogramme.cli.domain.Session
import java.time.Instant

/** Maps a facade authorization to the session a terminal client lists. */
internal fun AccountAuthorization.toSession(): Session = Session(
    hash = hash,
    deviceModel = deviceModel,
    platform = platform,
    appVersion = appVersion,
    ip = ip,
    country = country,
    createdAt = Instant.ofEpochMilli(dateCreated),
    current = current,
)
