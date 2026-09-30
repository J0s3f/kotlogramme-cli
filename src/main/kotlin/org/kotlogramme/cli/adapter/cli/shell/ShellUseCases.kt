package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages

/**
 * The inbound ports the shell dispatches to.
 *
 * Each is a provider rather than an instance because building a use case builds a Telegram client,
 * which should only happen for the command that needs it; a test can also hand in fakes directly.
 */
class ShellUseCases(
    val listDialogs: () -> ListDialogs,
    val readHistory: () -> ReadHistory,
    val messageWriter: () -> MessageWriter,
    val contacts: () -> Contacts,
    val searchMessages: () -> SearchMessages,
)
