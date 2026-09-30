package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.application.port.spi.UserGateway

/**
 * The [UserGateway] backed by the kotlogramme facade.
 *
 * `usersGetUsers` already deduplicates and omits what Telegram cannot resolve, so the answer maps
 * straight to ids. A resolved account with no username (or a blank one) is dropped rather than
 * mapped to an empty string, so the caller falls back to the id.
 */
internal class KotlogramUserGateway(private val operations: FacadeUserOperations) : UserGateway {
    override fun usernames(ids: List<Long>): Map<Long, String> =
        operations.users(ids.distinct())
            .mapNotNull { it.toUsernameEntry() }
            .toMap()

    private fun User.toUsernameEntry(): Pair<Long, String>? =
        username?.takeIf(String::isNotBlank)?.let { id to it }
}
