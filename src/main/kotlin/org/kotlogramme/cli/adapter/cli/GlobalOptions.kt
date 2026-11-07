package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.adapter.format.TableWidth
import org.kotlogramme.cli.application.port.spi.OutputFormat
import java.nio.file.Path

/** The options that come before the command name and apply to every command. */
data class GlobalOptions(
    /** The directory to use instead of the platform default, or `null` for the default. */
    val configDir: Path? = null,
    /** Never style message text with colour; wins over [color]. */
    val noColor: Boolean = false,
    /** Style message text with colour even when the output is not a terminal. */
    val color: Boolean = false,
    val tableWidth: TableWidth = TableWidth.Detect,
    /** The format for this run, or `null` to use the one the config stores. */
    val format: OutputFormat? = null,
)
