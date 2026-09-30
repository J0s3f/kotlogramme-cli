package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.User

/** The real [FacadeAccountOperations], delegating straight to the facade client. */
internal class KotlogramAccountOperations(private val client: TelegramClient) : FacadeAccountOperations {
    override fun isAuthorized(): Boolean = client.isAuthorized()

    override fun requestLoginCode(phoneNumber: String) {
        client.authSendCode(phoneNumber = phoneNumber)
    }

    override fun signIn(phoneNumber: String, phoneCode: String): User =
        client.authSignIn(phoneNumber, MANAGED_PHONE_CODE_HASH, phoneCode).user

    override fun checkPassword(password: String): User = client.authCheckPassword(password).user

    override fun importBotAuthorization(botToken: String): User = client.authImportBotAuthorization(botToken).user

    override fun signOut() {
        client.authLogOut()
    }

    override fun currentUser(): User = client.getMe()

    private companion object {
        // The facade retains the real Telegram token and ignores this hash on sign-in; the port
        // carries no hash, so a stable placeholder is enough.
        const val MANAGED_PHONE_CODE_HASH = "managed-by-kotlogramme"
    }
}
