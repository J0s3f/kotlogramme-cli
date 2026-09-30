package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.kotlogramme.cli.KotlogrammeCommand
import org.kotlogramme.cli.adapter.telegram.NativeLibraryCheck
import org.kotlogramme.cli.adapter.telegram.NativeLibraryProbe
import org.kotlogramme.cli.application.port.api.AccountStatus
import org.kotlogramme.cli.application.port.api.Authenticate
import org.kotlogramme.cli.application.port.api.LoginStep
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.application.port.spi.ConfigStore
import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Account
import java.nio.file.Path
import java.nio.file.Paths

/** Captures everything a command writes so a test can assert on it. */
internal class RecordingOutput : Output {
    private val recorded = mutableListOf<String>()

    val lines: List<String> get() = recorded

    val text: String get() = recorded.joinToString("\n")

    override fun line(text: String) {
        recorded += text
    }

    override fun table(headers: List<String>, rows: List<List<String>>, title: String?) {
        title?.let { recorded += it }
        recorded += headers.joinToString("\t")
        rows.forEach { recorded += it.joinToString("\t") }
    }
}

/** An in-memory [ConfigStore] that records the last value saved. */
internal class FakeConfigStore(private var config: AppConfig) : ConfigStore {
    override fun load(): AppConfig = config

    override fun save(config: AppConfig) {
        this.config = config
    }
}

/** An [Authenticate] that returns canned steps and records the calls it receives. */
internal class FakeAuthenticate(
    private val status: AccountStatus = AccountStatus.Anonymous,
    private val startStep: LoginStep = LoginStep.CodeRequired(testPhone, hint = null),
    private val codeStep: LoginStep = LoginStep.SignedIn(testAccount),
    private val passwordStep: LoginStep = LoginStep.SignedIn(testAccount),
) : Authenticate {
    val startedLogins = mutableListOf<String>()
    val submittedCodes = mutableListOf<Pair<String, String>>()
    val submittedPasswords = mutableListOf<String>()
    val botTokens = mutableListOf<String>()
    var logoutCount = 0

    override fun status(): AccountStatus = status

    override fun startLogin(phoneNumber: String): LoginStep {
        startedLogins += phoneNumber
        return startStep
    }

    override fun submitCode(phoneNumber: String, code: String): LoginStep {
        submittedCodes += phoneNumber to code
        return codeStep
    }

    override fun submitPassword(password: String): LoginStep {
        submittedPasswords += password
        return passwordStep
    }

    override fun loginWithBotToken(botToken: String): Account {
        botTokens += botToken
        return testAccount
    }

    override fun logout() {
        logoutCount++
    }
}

/** Drives the whole command tree the way `main` does, against injected fakes. */
internal class CliFixture(
    val output: RecordingOutput,
    val authenticate: FakeAuthenticate,
    private val root: KotlogrammeCommand,
) {
    fun run(vararg args: String) = root.test(args.toList())
}

internal fun cliFixture(
    config: AppConfig = AppConfig(
        credentials = ApiCredentials(apiId = 1, apiHash = "configured-hash"),
        sessionPath = Paths.get("session.sqlite"),
    ),
    configStore: ConfigStore = FakeConfigStore(config),
    output: RecordingOutput = RecordingOutput(),
    authenticate: FakeAuthenticate = FakeAuthenticate(),
    configDir: Path = Paths.get("config"),
    environment: Map<String, String> = emptyMap(),
    nativeLibraryProbe: NativeLibraryProbe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Loaded(null) },
): CliFixture {
    val context = AppContext(configDir, configStore, output, environment) { authenticate }
    val root = KotlogrammeCommand { context }
        .subcommands(ConfigCommand(), LoginCommand(), LogoutCommand(), WhoamiCommand(), DoctorCommand(nativeLibraryProbe))
    return CliFixture(output, authenticate, root)
}

internal val testAccount = Account(
    id = 42,
    firstName = "Ada",
    lastName = "Lovelace",
    username = "ada",
    phoneNumber = "+15550100",
)

internal const val testPhone = "+15550142"
