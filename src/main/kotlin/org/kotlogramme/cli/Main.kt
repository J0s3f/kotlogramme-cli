package org.kotlogramme.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import org.kotlogramme.cli.adapter.cli.AppContext
import org.kotlogramme.cli.adapter.cli.ConfigCommand
import org.kotlogramme.cli.adapter.cli.LoginCommand
import org.kotlogramme.cli.adapter.cli.LogoutCommand
import org.kotlogramme.cli.adapter.cli.WhoamiCommand
import java.nio.file.Path

/** The root of the command tree. Every user-facing command hangs off this. */
class KotlogrammeCommand(
    private val appContextFactory: (Path?) -> AppContext = { AppContext.create(it) },
) : CliktCommand(name = "kotlogramme") {
    private val configDir by option(
        "--config-dir",
        help = "Use this directory instead of the platform default",
    ).path()

    override fun run() {
        currentContext.obj = appContextFactory(configDir)
    }
}

fun main(args: Array<String>) {
    KotlogrammeCommand()
        .versionOption(VERSION)
        .subcommands(ConfigCommand(), LoginCommand(), LogoutCommand(), WhoamiCommand())
        .main(args)
}
