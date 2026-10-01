package org.kotlogramme.cli.adapter.cli.shell

import org.kotlogramme.cli.application.port.api.ChatMembers
import org.kotlogramme.cli.application.port.api.Contacts
import org.kotlogramme.cli.application.port.api.DownloadMedia
import org.kotlogramme.cli.application.port.api.InlineBots
import org.kotlogramme.cli.application.port.api.ListDialogs
import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.api.MessageWriter
import org.kotlogramme.cli.application.port.api.ReadHistory
import org.kotlogramme.cli.application.port.api.SearchMessages
import org.kotlogramme.cli.application.port.api.SendMedia
import org.kotlogramme.cli.application.port.api.Sessions
import org.kotlogramme.cli.application.port.api.Stickers

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
    val chatMembers: () -> ChatMembers,
    val listFolders: () -> ListFolders,
    val stickers: () -> Stickers,
    val inline: () -> InlineBots,
    val sendMedia: () -> SendMedia,
    val downloadMedia: () -> DownloadMedia,
    val sessions: () -> Sessions,
)
