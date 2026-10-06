package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.VERSION
import org.kotlogramme.cli.adapter.diagnostic.AccountDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.CredentialsDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.ConfigurationDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.Endpoint
import org.kotlogramme.cli.adapter.diagnostic.NativeLibraryDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.NetworkDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.RuntimeDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.SessionDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.TelegramSchemaDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.TemporaryDirectoryDiagnostic
import org.kotlogramme.cli.adapter.diagnostic.TerminalDiagnostic
import org.kotlogramme.cli.application.port.spi.Diagnostic
import java.nio.file.Path

/**
 * The checks `doctor` runs, in the order they are listed.
 *
 * What the installation itself needs comes first and needs nothing configured; what depends on the
 * user's configuration follows, and the check that talks to Telegram with their credentials is last.
 * A configuration that cannot be read must not stop the others, so credentials are looked up
 * defensively and a broken configuration is reported by its own check.
 */
internal fun standardDiagnostics(appContext: AppContext): List<Diagnostic> {
    val credentials = { appContext.credentials() }
    val hasCredentials = { runCatching(credentials).getOrNull() != null }
    return listOf(
        RuntimeDiagnostic(VERSION),
        NativeLibraryDiagnostic(),
        TelegramSchemaDiagnostic(),
        TerminalDiagnostic(),
        TemporaryDirectoryDiagnostic(Path.of(System.getProperty("java.io.tmpdir"))),
        ConfigurationDiagnostic(appContext.configDir) { appContext.config() },
        CredentialsDiagnostic(credentials),
        SessionDiagnostic { appContext.config().sessionPath },
        NetworkDiagnostic(TELEGRAM_ENDPOINTS),
        AccountDiagnostic(hasCredentials) { appContext.authenticate().status() },
    )
}

/** The website, which needs DNS, and a production data centre address, which needs only a route. */
private val TELEGRAM_ENDPOINTS = listOf(
    Endpoint("telegram.org", "telegram.org", 443),
    Endpoint("DC2", "149.154.167.51", 443),
)
