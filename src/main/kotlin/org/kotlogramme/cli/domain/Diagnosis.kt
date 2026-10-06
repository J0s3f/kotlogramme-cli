package org.kotlogramme.cli.domain

/** How one diagnostic check came out. Only [FAILED] makes `doctor` fail. */
enum class DiagnosticStatus(val label: String) {
    OK("ok"),

    /** Something is off but the client still works, such as no credentials yet or no network. */
    WARNING("warning"),

    /** The check needs something that is not configured, so it was not run. */
    SKIPPED("skipped"),

    FAILED("FAILED"),
}

/** What a check found: how it came out and a sentence a person can act on. */
data class Finding(val status: DiagnosticStatus, val detail: String) {
    companion object {
        fun ok(detail: String) = Finding(DiagnosticStatus.OK, detail)

        fun warning(detail: String) = Finding(DiagnosticStatus.WARNING, detail)

        fun skipped(detail: String) = Finding(DiagnosticStatus.SKIPPED, detail)

        fun failed(detail: String) = Finding(DiagnosticStatus.FAILED, detail)
    }
}

/** A [Finding] together with the name of the check that made it. */
data class DiagnosticResult(val check: String, val status: DiagnosticStatus, val detail: String)
