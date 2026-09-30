package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject

/** Signs the current session out. */
class LogoutCommand : CliktCommand(name = "logout") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        appContext.authenticate().logout()
        appContext.output.line("Signed out.")
    }
}
