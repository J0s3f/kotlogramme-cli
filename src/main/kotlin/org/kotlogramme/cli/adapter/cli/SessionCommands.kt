package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import org.kotlogramme.cli.adapter.format.renderSessions

/**
 * The account's active sessions.
 *
 * On its own it lists them; `terminate <hash>` drops one and `terminate-all` drops every other.
 * Termination is irreversible and security-sensitive, so a single termination needs the exact hash
 * the listing reported and `terminate-all` needs `--yes` rather than acting on the first mistake.
 */
class SessionsCommand : CliktCommand(name = "sessions") {
    private val appContext by requireObject<AppContext>()

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(TerminateSessionCommand(), TerminateAllSessionsCommand())
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            appContext.output.renderSessions(rejectInvalidInput { appContext.sessions().list() })
        }
    }
}

/** Drops one active session, named by the hash its listing reported. */
class TerminateSessionCommand : CliktCommand(name = "terminate") {
    private val appContext by requireObject<AppContext>()

    private val hash by argument("hash", help = "The session hash from the `sessions` listing").long()

    override fun run() {
        rejectInvalidInput { appContext.sessions().terminate(hash) }
        appContext.output.line("Terminated session $hash.")
    }
}

/** Drops every active session except the current one. */
class TerminateAllSessionsCommand : CliktCommand(name = "terminate-all") {
    private val appContext by requireObject<AppContext>()

    private val yes by option("--yes", help = "Confirm dropping every other session").flag()

    override fun run() {
        if (!yes) {
            throw UsageError(
                "This terminates every session except the current one, and cannot be undone. " +
                    "Re-run with --yes to confirm.",
            )
        }
        rejectInvalidInput { appContext.sessions().terminateAll() }
        appContext.output.line("Terminated every session except the current one.")
    }
}
