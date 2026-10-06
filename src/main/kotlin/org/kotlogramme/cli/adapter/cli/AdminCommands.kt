package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import org.kotlogramme.cli.adapter.format.renderChatRights
import org.kotlogramme.cli.domain.ChatRights
import java.time.Duration
import java.time.Instant

/** Shows a member's admin rights, granted and denied. */
class PermissionsCommand : CliktCommand(name = "permissions") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val user by argument("user", help = "The member: @username, numeric id or invite link")

    override fun run() {
        val rights = rejectInvalidInput { appContext.adminRights().permissions(peer, user) }
        appContext.output.renderChatRights(rights)
    }
}

/** Promotes a member with the named rights, or every right with `--all`. */
class PromoteCommand : CliktCommand(name = "promote") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val user by argument("user", help = "The member: @username, numeric id or invite link")
    private val grant by option(
        "--grant",
        help = "Comma-separated rights to grant: ${RightsOptions.ADMIN_NAMES.joinToString(", ")}",
    ).split(",")
    private val all by option("--all", help = "Grant every right").flag()

    override fun run() {
        val rights = selectedRights()
        rejectInvalidInput { appContext.adminRights().promote(peer, user, rights) }
        appContext.output.line("Promoted $user in $peer.")
    }

    private fun selectedRights(): ChatRights =
        if (all) ChatRights.ALL else rejectInvalidInput {
            RightsOptions.rights(RightsOptions.select(grant.orEmpty(), RightsOptions.ADMIN_NAMES))
        }
}

/**
 * Bans or restricts a member.
 *
 * The default takes everything away for [DEFAULT_BAN_HOURS] hours; `--allow` keeps the named
 * abilities and `--forever` lifts the expiry.
 */
class RestrictCommand : CliktCommand(name = "restrict") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val user by argument("user", help = "The member: @username, numeric id or invite link")
    private val allow by option(
        "--allow",
        help = "Comma-separated abilities to keep: ${RightsOptions.RESTRICTION_NAMES.joinToString(", ")}",
    ).split(",")
    private val forever by option("--forever", help = "Do not lift the restriction").flag()

    override fun run() {
        val restrictions = rejectInvalidInput {
            RightsOptions.restrictions(
                RightsOptions.select(allow.orEmpty(), RightsOptions.RESTRICTION_NAMES),
                expiry(),
            )
        }
        rejectInvalidInput { appContext.adminRights().restrict(peer, user, restrictions) }
        appContext.output.line(summary())
    }

    private fun expiry(): Instant? = if (forever) null else appContext.clock.now().plus(DEFAULT_BAN)

    private fun summary(): String =
        if (forever) "Restricted $user in $peer, forever." else "Restricted $user in $peer for ${DEFAULT_BAN_HOURS}h."

    private companion object {
        /** How long a restriction lasts unless `--forever` asks for it to never lift. */
        const val DEFAULT_BAN_HOURS = 24

        val DEFAULT_BAN: Duration = Duration.ofHours(DEFAULT_BAN_HOURS.toLong())
    }
}
