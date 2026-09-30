package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.api.TelegramApp
import com.github.badoualy.telegram.api.TelegramClient
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import java.nio.file.Path

/**
 * Creates the facade client from the credentials and the session file.
 *
 * The facade persists the whole session in [sessionPath]; the application never touches its
 * internals. Kept a plain factory so the composition root is the only place that knows how a
 * client is built.
 */
internal class TelegramClientFactory {
    fun create(credentials: ApiCredentials, sessionPath: Path): TelegramClient =
        Kotlogram.getDefaultClient(
            TelegramApp(apiId = credentials.apiId, apiHash = credentials.apiHash),
            sessionPath,
        )
}
