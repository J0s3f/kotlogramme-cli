package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.OutputFormat

/** Shows the resolved configuration, and hosts `config set`. */
class ConfigCommand : CliktCommand(name = "config") {
    private val appContext by requireObject<AppContext>()

    init {
        subcommands(ConfigSetCommand())
    }

    // `config` prints the summary itself; the set subcommand only runs when named.
    override val invokeWithoutSubcommand: Boolean = true

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        val config = appContext.config()
        appContext.output.line("Config directory: ${appContext.configDir}")
        appContext.output.line("Session path: ${config.sessionPath}")
        appContext.output.line("Credentials: ${describe(appContext.credentials(config))}")
        appContext.output.line("Output format: ${config.outputFormat.name.lowercase()}")
    }

    private fun describe(credentials: ApiCredentials?): String =
        if (credentials == null) "not set" else "set (API id ${credentials.apiId})"
}

/** Persists the API credentials, and optionally the default output format. */
class ConfigSetCommand : CliktCommand(name = "set") {
    private val appContext by requireObject<AppContext>()

    private val apiId by option("--api-id", help = "The API id from my.telegram.org").int().required()
    private val apiHash by option("--api-hash", help = "The API hash from my.telegram.org").required()
    private val format by option("--format", help = "Default output format: table, plain or json")

    override fun run() {
        val current = appContext.config()
        val updated = current.copy(
            credentials = ApiCredentials(apiId, apiHash),
            outputFormat = format?.let(::parseFormat) ?: current.outputFormat,
        )
        appContext.save(updated)
        appContext.output.line("Configuration saved in ${appContext.configDir}")
    }

    private fun parseFormat(value: String): OutputFormat =
        OutputFormat.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw UsageError("Unknown output format '$value'; choose table, plain or json.")
}
