package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Authorizations
import com.github.badoualy.telegram.api.TelegramClient

/**
 * The facade session calls the session gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramSessionGateway] can be exercised without a live client. Each method
 * mirrors one facade operation; the facade model is mapped to the domain at the gateway boundary.
 */
internal interface FacadeSessionOperations {
    /** Lists the account's active sessions, which is `accountGetAuthorizations`. */
    fun authorizations(): Authorizations

    /** Drops one session by [hash], which is `accountResetAuthorization`. */
    fun resetAuthorization(hash: Long)

    /** Drops every session but the current one, which is `accountResetAuthorizations`. */
    fun resetAuthorizations()
}

/** The real [FacadeSessionOperations], delegating straight to the facade client. */
internal class KotlogramSessionOperations(private val client: TelegramClient) : FacadeSessionOperations {
    override fun authorizations(): Authorizations = client.accountGetAuthorizations()

    override fun resetAuthorization(hash: Long) = client.accountResetAuthorization(hash)

    override fun resetAuthorizations() = client.accountResetAuthorizations()
}
