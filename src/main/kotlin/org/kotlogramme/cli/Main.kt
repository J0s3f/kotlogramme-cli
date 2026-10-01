package org.kotlogramme.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import org.kotlogramme.cli.adapter.cli.AppContext
import org.kotlogramme.cli.adapter.cli.BlockCommand
import org.kotlogramme.cli.adapter.cli.BlockedCommand
import org.kotlogramme.cli.adapter.cli.ChatActionCommand
import org.kotlogramme.cli.adapter.cli.ChatPhotosCommand
import org.kotlogramme.cli.adapter.cli.ConfigCommand
import org.kotlogramme.cli.adapter.cli.ContactsCommand
import org.kotlogramme.cli.adapter.cli.CopyMediaCommand
import org.kotlogramme.cli.adapter.cli.DeleteCommand
import org.kotlogramme.cli.adapter.cli.DeleteContactCommand
import org.kotlogramme.cli.adapter.cli.DialogsCommand
import org.kotlogramme.cli.adapter.cli.DoctorCommand
import org.kotlogramme.cli.adapter.cli.DownloadMediaCommand
import org.kotlogramme.cli.adapter.cli.EditCommand
import org.kotlogramme.cli.adapter.cli.FoldersCommand
import org.kotlogramme.cli.adapter.cli.ForwardCommand
import org.kotlogramme.cli.adapter.cli.HistoryCommand
import org.kotlogramme.cli.adapter.cli.ImportContactsCommand
import org.kotlogramme.cli.adapter.cli.InlineCommand
import org.kotlogramme.cli.adapter.cli.InviteCommand
import org.kotlogramme.cli.adapter.cli.KickCommand
import org.kotlogramme.cli.adapter.cli.ListFilesCommand
import org.kotlogramme.cli.adapter.cli.ListenCommand
import org.kotlogramme.cli.adapter.cli.LoginCommand
import org.kotlogramme.cli.adapter.cli.LogoutCommand
import org.kotlogramme.cli.adapter.cli.MarkReadCommand
import org.kotlogramme.cli.adapter.cli.MembersCommand
import org.kotlogramme.cli.adapter.cli.PermissionsCommand
import org.kotlogramme.cli.adapter.cli.PinCommand
import org.kotlogramme.cli.adapter.cli.PinnedCommand
import org.kotlogramme.cli.adapter.cli.ProfilePhotosCommand
import org.kotlogramme.cli.adapter.cli.PromoteCommand
import org.kotlogramme.cli.adapter.cli.ReactCommand
import org.kotlogramme.cli.adapter.cli.RestrictCommand
import org.kotlogramme.cli.adapter.cli.SearchCommand
import org.kotlogramme.cli.adapter.cli.SearchContactsCommand
import org.kotlogramme.cli.adapter.cli.SendCommand
import org.kotlogramme.cli.adapter.cli.SendFileCommand
import org.kotlogramme.cli.adapter.cli.SendMediaUrlCommand
import org.kotlogramme.cli.adapter.cli.SendStickerCommand
import org.kotlogramme.cli.adapter.cli.SessionsCommand
import org.kotlogramme.cli.adapter.cli.ShellCommand
import org.kotlogramme.cli.adapter.cli.StickerSetCommand
import org.kotlogramme.cli.adapter.cli.StickersCommand
import org.kotlogramme.cli.adapter.cli.UnblockCommand
import org.kotlogramme.cli.adapter.cli.UnpinCommand
import org.kotlogramme.cli.adapter.cli.UnreactCommand
import org.kotlogramme.cli.adapter.cli.WhoamiCommand
import org.kotlogramme.TelegramException
import java.nio.file.Path
import kotlin.system.exitProcess

/** The root of the command tree. Every user-facing command hangs off this. */
class KotlogrammeCommand(
    private val appContextFactory: (Path?, Boolean, Boolean) -> AppContext = { directory, noColor, color ->
        AppContext.create(directory, noColor, color)
    },
) : CliktCommand(name = "kotlogramme") {
    private val configDir by option(
        "--config-dir",
        help = "Use this directory instead of the platform default",
    ).path()

    private val noColor by option(
        "--no-color",
        help = "Never style message text with ANSI colour (as NO_COLOR does, and wins over --color)",
    ).flag()

    private val color by option(
        "--color",
        help = "Style message text with ANSI colour even off a terminal, for a pager such as less -R",
    ).flag()

    init {
        // A chat reference is `@username`, so `@` must not be read as an argument file.
        context {
            readArgumentFile = null
        }
    }

    override fun run() {
        currentContext.obj = appContextFactory(configDir, noColor, color)
    }
}

fun main(args: Array<String>) {
    try {
        KotlogrammeCommand()
            .versionOption(VERSION)
            .subcommands(
                ConfigCommand(),
                LoginCommand(),
                LogoutCommand(),
                WhoamiCommand(),
                DoctorCommand(),
                DialogsCommand(),
                HistoryCommand(),
                SendCommand(),
                SendFileCommand(),
                SendMediaUrlCommand(),
                CopyMediaCommand(),
                DownloadMediaCommand(),
                ListFilesCommand(),
                EditCommand(),
                DeleteCommand(),
                ForwardCommand(),
                PinCommand(),
                UnpinCommand(),
                PinnedCommand(),
                ReactCommand(),
                UnreactCommand(),
                MarkReadCommand(),
                ChatActionCommand(),
                ContactsCommand(),
                SearchContactsCommand(),
                BlockCommand(),
                UnblockCommand(),
                BlockedCommand(),
                ImportContactsCommand(),
                DeleteContactCommand(),
                SearchCommand(),
                MembersCommand(),
                InviteCommand(),
                KickCommand(),
                PermissionsCommand(),
                PromoteCommand(),
                RestrictCommand(),
                ListenCommand(),
                FoldersCommand(),
                SessionsCommand(),
                ChatPhotosCommand(),
                ProfilePhotosCommand(),
                StickersCommand(),
                StickerSetCommand(),
                SendStickerCommand(),
                InlineCommand(),
                ShellCommand(),
            )
            .main(args)
    } catch (error: TelegramException) {
        // A rejected request is a normal outcome, not a crash: the live run showed this dumping a
        // Java stack trace at the user.
        System.err.println("Error: ${error.message ?: "the Telegram request failed."}")
        exitProcess(1)
    } catch (error: RuntimeException) {
        System.err.println("Error: ${error.message ?: error::class.qualifiedName}")
        exitProcess(1)
    }
}
