package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramUpdate

/** The real [FacadeUpdateOperations], delegating straight to the facade client. */
internal class KotlogramUpdateOperations(private val client: TelegramClient) : FacadeUpdateOperations {
    override fun next(timeoutMillis: Long): TelegramUpdate? = client.getNextUpdate(timeoutMillis)

    override fun syncState() = client.syncUpdateState()
}
