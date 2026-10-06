package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.DiagnosticResult

/** The inbound port that checks the installation and reports one result per check. */
interface Doctor {
    fun run(): List<DiagnosticResult>
}
