package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import org.kotlogramme.cli.adapter.format.renderFolders

/** Lists the account's dialog folders. */
class FoldersCommand : CliktCommand(name = "folders") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        appContext.output.renderFolders(appContext.listFolders().list())
    }
}
