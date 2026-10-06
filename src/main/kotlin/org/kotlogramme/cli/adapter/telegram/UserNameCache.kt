package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a user id to the name a person recognises, asking Telegram once per id.
 *
 * An update names the peer or sender of a message only by id when the account has not seen that user
 * before, such as a message you send to a contact from another client. Looking the id up costs a
 * network call, and a conversation produces many updates, so a resolved name is kept for [lifetime],
 * after which it is asked for again so a renamed contact is not shown under the old name for as long
 * as the process runs. An id Telegram does not resolve is asked about again on its next update,
 * because the answer may be a transient failure. A failed lookup yields no name: the caller falls back to
 * the id, and the update loop, which a throwing callback would end, keeps running.
 */
internal class UserNameCache(
    private val users: FacadeUserOperations,
    private val clock: Clock,
    private val lifetime: Duration = NAME_LIFETIME,
) {
    private class ResolvedName(val name: String, val expiresAt: Instant)

    private val names = ConcurrentHashMap<Long, ResolvedName>()

    val size: Int get() = names.size

    fun nameOf(id: Long): String? = names[id]?.takeIf { it.isValid() }?.name
        ?: lookUp(id)?.also { remember(id, it) }

    private fun ResolvedName.isValid() = expiresAt.isAfter(clock.now())

    private fun remember(id: Long, name: String) {
        forgetExpired()
        names[id] = ResolvedName(name, clock.now().plus(lifetime))
    }

    /** Without this a long `listen` keeps every name it ever saw: only asking for the same id again replaces one. */
    private fun forgetExpired() {
        names.values.removeIf { !it.isValid() }
    }

    /** Drops a cached name because Telegram reported that the user changed. */
    fun forget(id: Long) {
        names.remove(id)
    }

    private fun lookUp(id: Long): String? =
        runCatching { users.users(listOf(id)).firstOrNull()?.displayName() }.getOrNull()
}

private val NAME_LIFETIME = Duration.ofHours(3)
