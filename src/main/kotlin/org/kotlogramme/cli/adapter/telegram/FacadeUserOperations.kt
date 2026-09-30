package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User

/**
 * The facade user call the user gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramUserGateway] can be exercised without a live client.
 */
internal interface FacadeUserOperations {
    /** Resolves [ids] to the accounts they name, which is `usersGetUsers`. */
    fun users(ids: List<Long>): List<User>
}
