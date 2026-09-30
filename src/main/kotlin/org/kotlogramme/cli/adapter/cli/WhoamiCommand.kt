package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.kotlogramme.cli.application.port.api.AccountStatus

/** Prints the signed-in account, or a clear line when the session is anonymous. */
class WhoamiCommand : CliktCommand(name = "whoami") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        when (val status = appContext.authenticate().status()) {
            is AccountStatus.SignedIn -> appContext.output.printAccount(status.account)
            is AccountStatus.Anonymous -> appContext.output.line("Not signed in.")
        }
    }
}
