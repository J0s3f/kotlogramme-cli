package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User

/**
 * The facade auth calls the gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramAccountGateway] can be exercised without creating a live client. The
 * methods mirror the facade's auth operations; [User] is the facade model and is mapped to the
 * domain at the gateway boundary.
 */
internal interface FacadeAccountOperations {
    fun isAuthorized(): Boolean

    fun requestLoginCode(phoneNumber: String)

    fun signIn(phoneNumber: String, phoneCode: String): User

    fun checkPassword(password: String): User

    fun importBotAuthorization(botToken: String): User

    fun signOut()

    fun currentUser(): User
}
