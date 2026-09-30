package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderParticipants

/** Lists a chat's members. */
class MembersCommand : CliktCommand(name = "members") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val limit by option("--limit", help = "How many members to list").int().default(DEFAULT_LIMIT)

    override fun run() {
        appContext.output.renderParticipants(rejectInvalidInput { appContext.chatMembers().list(peer, limit) })
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/** Adds a member to a chat. */
class InviteCommand : CliktCommand(name = "invite") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val user by argument("user", help = "The member: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.chatMembers().invite(peer, user) }
        appContext.output.line("Invited $user to $peer.")
    }
}

/** Removes a member from a chat. */
class KickCommand : CliktCommand(name = "kick") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val user by argument("user", help = "The member: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.chatMembers().kick(peer, user) }
        appContext.output.line("Kicked $user from $peer.")
    }
}
