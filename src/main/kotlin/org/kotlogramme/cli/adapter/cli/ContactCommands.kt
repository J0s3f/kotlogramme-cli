package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderBlockedContacts
import org.kotlogramme.cli.adapter.format.renderContacts
import org.kotlogramme.cli.domain.ContactToImport

/** Searches the account's contacts and the public directory. */
class SearchContactsCommand : CliktCommand(name = "search-contacts") {
    private val appContext by requireObject<AppContext>()

    private val query by argument("query", help = "The text to search for")
    private val limit by option("--limit", help = "How many matches to list").int().default(DEFAULT_LIMIT)

    override fun run() {
        appContext.output.renderContacts(rejectInvalidInput { appContext.contacts().search(query, limit) })
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/** Blocks a peer, so they can no longer message the account. */
class BlockCommand : CliktCommand(name = "block") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.contacts().block(peer) }
        appContext.output.line("Blocked $peer.")
    }
}

/** Unblocks a peer who was previously blocked. */
class UnblockCommand : CliktCommand(name = "unblock") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.contacts().unblock(peer) }
        appContext.output.line("Unblocked $peer.")
    }
}

/** Lists the peers the account has blocked. */
class BlockedCommand : CliktCommand(name = "blocked") {
    private val appContext by requireObject<AppContext>()

    private val limit by option("--limit", help = "How many blocked peers to list").int().default(DEFAULT_LIMIT)

    override fun run() {
        appContext.output.renderBlockedContacts(rejectInvalidInput { appContext.contacts().blocked(limit) })
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/**
 * Imports a phone number as a saved contact.
 *
 * One contact per call: a name belongs to one person, and naming several at once would guess. The
 * command reports what Telegram actually saved, which for a number with no Telegram account is
 * nothing.
 */
class ImportContactsCommand : CliktCommand(name = "import-contacts") {
    private val appContext by requireObject<AppContext>()

    private val phone by argument("phone", help = "The phone number to import, in international form")
    private val firstName by argument("firstName", help = "The first name to save")
    private val lastName by argument("lastName", help = "The last name to save").optional()

    override fun run() {
        val summary = rejectInvalidInput {
            appContext.contacts().import(listOf(ContactToImport(phone, firstName, lastName.orEmpty())))
        }
        appContext.output.line("Imported ${summary.imported.size} of 1 contact(s).")
        if (summary.retryCount > 0) {
            appContext.output.line("Telegram asked to retry ${summary.retryCount} contact(s).")
        }
    }
}

/**
 * Removes a peer from the account's saved contacts.
 *
 * The peer stays reachable; this only forgets the saved contact, it does not block. Use `block` to
 * stop messages from them.
 */
class DeleteContactCommand : CliktCommand(name = "delete-contact") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The contact to delete: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.contacts().delete(peer) }
        appContext.output.line("Deleted $peer from contacts. The peer is not blocked.")
    }
}
