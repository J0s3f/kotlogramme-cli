package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.PasswordRequiredException
import org.kotlogramme.cli.application.port.spi.AccountGateway
import org.kotlogramme.cli.application.port.spi.SignInOutcome
import org.kotlogramme.cli.domain.Account

/**
 * The [AccountGateway] backed by the kotlogramme facade.
 *
 * The facade reports a second factor by throwing [PasswordRequiredException]; the port models it as
 * [SignInOutcome.PasswordRequired], so the translation happens here and nowhere above.
 */
internal class KotlogramAccountGateway(
    private val operations: FacadeAccountOperations,
) : AccountGateway {
    override fun isAuthorized(): Boolean = operations.isAuthorized()

    override fun requestLoginCode(phoneNumber: String) {
        operations.requestLoginCode(phoneNumber)
    }

    override fun signIn(phoneNumber: String, phoneCode: String): SignInOutcome =
        try {
            SignInOutcome.SignedIn(operations.signIn(phoneNumber, phoneCode).toAccount())
        } catch (exception: PasswordRequiredException) {
            SignInOutcome.PasswordRequired(exception.hint)
        }

    override fun checkPassword(password: String): Account = operations.checkPassword(password).toAccount()

    override fun importBotAuthorization(botToken: String): Account =
        operations.importBotAuthorization(botToken).toAccount()

    override fun signOut() {
        operations.signOut()
    }

    override fun currentAccount(): Account = operations.currentUser().toAccount()
}
