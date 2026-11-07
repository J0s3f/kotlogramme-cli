package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderBlockedContacts
import org.kotlogramme.cli.adapter.format.renderContacts
import org.kotlogramme.cli.application.ListingCursor
import org.kotlogramme.cli.domain.ContactToImport
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

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
        appContext.output.message("Blocked $peer.")
    }
}

/** Unblocks a peer who was previously blocked. */
class UnblockCommand : CliktCommand(name = "unblock") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")

    override fun run() {
        rejectInvalidInput { appContext.contacts().unblock(peer) }
        appContext.output.message("Unblocked $peer.")
    }
}

/** Lists the peers the account has blocked. */
class BlockedCommand : CliktCommand(name = "blocked") {
    private val appContext by requireObject<AppContext>()

    private val limit by option("--limit", help = "How many blocked peers to list").int().default(DEFAULT_LIMIT)
    private val after by option("--after", help = "The cursor to continue from, as the last page printed it")
    private val all by option(
        "--all",
        help = "List every blocked peer in one call, ignoring the cursor and the limit",
    ).flag()

    override fun run() {
        val blocked = rejectInvalidInput { appContext.contacts().blocked(limit, after, all) }
        appContext.output.renderBlockedContacts(blocked)
        if (blocked.size == limit && !all) {
            val startingOffset = after?.let(ListingCursor::parseDecimal) ?: 0
            appContext.output.nextPage((startingOffset + blocked.size).toString())
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/**
 * Imports phone numbers as saved contacts.
 *
 * One contact comes from the arguments, because a name belongs to one person and naming several at
 * once on a command line would guess. Many come from `--from-json`, a JSON array of
 * `{"phone", "first_name", "last_name"}` objects read from a file or from standard input (`-`), which
 * is how a program hands over an address book. The command reports what Telegram actually saved, which
 * for a number with no Telegram account is nothing.
 */
class ImportContactsCommand(
    private val stdin: InputStream = System.`in`,
) : CliktCommand(name = "import-contacts") {
    private val appContext by requireObject<AppContext>()

    private val phone by argument("phone", help = "The phone number to import, in international form").optional()
    private val firstName by argument("firstName", help = "The first name to save").optional()
    private val lastName by argument("lastName", help = "The last name to save").optional()
    private val fromJson by option(
        "--from-json",
        metavar = "FILE",
        help = "Import every contact of this JSON array instead (- for stdin): " +
            "[{\"phone\": \"+1555\", \"first_name\": \"Ada\", \"last_name\": \"Lovelace\"}]",
    )

    override fun run() {
        val contacts = contactsToImport()
        val summary = rejectInvalidInput { appContext.contacts().import(contacts) }
        appContext.output.message("Imported ${summary.imported.size} of ${contacts.size} contact(s).")
        if (summary.retryCount > 0) {
            appContext.output.message("Telegram asked to retry ${summary.retryCount} contact(s).")
        }
    }

    private fun contactsToImport(): List<ContactToImport> {
        val source = fromJson
        if (source == null) return listOf(contactFromArguments())
        if (phone != null) throw UsageError("Give either a phone number or --from-json, not both.")
        return contactsFromJson(source)
    }

    private fun contactFromArguments(): ContactToImport {
        val number = phone ?: throw UsageError("Missing argument 'phone' (or give --from-json).")
        val first = firstName ?: throw UsageError("Missing argument 'firstName'.")
        return ContactToImport(number, first, lastName.orEmpty())
    }

    private fun contactsFromJson(source: String): List<ContactToImport> {
        val text = if (source == STDIN) String(stdin.readAllBytes(), Charsets.UTF_8) else readFile(source)
        val contacts = try {
            Json.decodeFromString<List<ContactJson>>(text)
        } catch (error: SerializationException) {
            throw UsageError("Expected a JSON array of {phone, first_name, last_name} objects: ${error.message}")
        }
        if (contacts.isEmpty()) throw UsageError("The JSON array holds no contacts.")
        return contacts.map { ContactToImport(it.phone, it.firstName, it.lastName) }
    }

    private fun readFile(path: String): String =
        try {
            Files.readString(Path.of(path))
        } catch (error: IOException) {
            throw UsageError("Cannot read $path: ${error.message}")
        }

    @Serializable
    private data class ContactJson(
        val phone: String,
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String = "",
    )

    private companion object {
        const val STDIN = "-"
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
        appContext.output.message("Deleted $peer from contacts. The peer is not blocked.")
    }
}
