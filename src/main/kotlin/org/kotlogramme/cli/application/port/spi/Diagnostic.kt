package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Finding

/**
 * One check of the installation or its environment, as an outbound port.
 *
 * A diagnostic looks at the machine, the libraries or Telegram and reports what it found. It does
 * not have to be safe to fail: a diagnostic may throw, and the runner turns that into a failed
 * finding for this check alone.
 */
interface Diagnostic {
    /** The label the check is listed under. */
    val name: String

    fun run(): Finding
}
