package org.kotlogramme.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.versionOption

/** The root of the command tree. Every user-facing command hangs off this. */
class KotlogrammeCommand : CliktCommand(name = "kotlogramme") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    KotlogrammeCommand()
        .versionOption(VERSION)
        .main(args)
}
