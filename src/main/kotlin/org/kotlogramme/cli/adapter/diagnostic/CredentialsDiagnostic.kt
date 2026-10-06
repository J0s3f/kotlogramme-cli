package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding

/**
 * Checks that API credentials are set and look like what `my.telegram.org` issues.
 *
 * Credentials are optional for `doctor`; their absence is a warning that says how to set them, and
 * the hash is never printed.
 */
class CredentialsDiagnostic(private val credentials: () -> ApiCredentials?) : Diagnostic {
    override val name = "Credentials"

    override fun run(): Finding {
        val set = credentials() ?: return Finding.warning(
            "not set; use TG_API_ID and TG_API_HASH or `config set --api-id ... --api-hash ...`",
        )
        return when {
            set.apiId <= 0 -> Finding.warning("the API id must be a positive number, not ${set.apiId}")
            !HASH_FORMAT.matches(set.apiHash) -> Finding.warning("the API hash should be 32 hex characters")
            else -> Finding.ok("API id ${set.apiId}, hash set")
        }
    }

    private companion object {
        val HASH_FORMAT = Regex("[0-9a-fA-F]{32}")
    }
}
