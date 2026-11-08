package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramClient
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import java.nio.file.Path

/**
 * Where use cases get their Telegram client from.
 *
 * A one-shot command builds a client per use case and the process ends right after, so by default every
 * request gets a fresh one, exactly as before this class existed. A process that lives for hours, such as
 * the MCP server, switches [reuseOneClient] on: one connection then serves every use case, which is also
 * what a session file that one process holds open expects. The client is not thread safe, so callers
 * serialise their use of it.
 */
class TelegramClients<C : AutoCloseable>(private val create: (ApiCredentials, Path) -> C) {
    private var reuse = false
    private var shared: Shared<C>? = null

    fun reuseOneClient() {
        reuse = true
    }

    fun clientFor(credentials: ApiCredentials, session: Path): C {
        if (!reuse) return create(credentials, session)
        val key = Key(credentials, session)
        shared?.takeIf { it.key == key }?.let { return it.client }
        shared?.client?.close()
        return create(credentials, session).also { shared = Shared(key, it) }
    }

    /** Closes the shared client, if there is one. Clients handed out without reuse belong to their caller. */
    fun close() {
        shared?.client?.close()
        shared = null
    }

    private data class Key(val credentials: ApiCredentials, val session: Path)

    private class Shared<C>(val key: Key, val client: C)
}

/** The production source: real clients from the facade. */
internal fun telegramClients(): TelegramClients<TelegramClient> =
    TelegramClients { credentials, session -> TelegramClientFactory().create(credentials, session) }
