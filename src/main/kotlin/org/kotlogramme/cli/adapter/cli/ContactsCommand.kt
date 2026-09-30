package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderContacts

/** Lists the account's contacts. */
class ContactsCommand : CliktCommand(name = "contacts") {
    private val appContext by requireObject<AppContext>()

    private val limit by option("--limit", help = "How many contacts to list").int().default(DEFAULT_LIMIT)

    override fun run() {
        appContext.output.renderContacts(rejectInvalidInput { appContext.contacts().list(limit) })
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}
