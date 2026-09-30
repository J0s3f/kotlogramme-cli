package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.User

/** The real [FacadeUserOperations], delegating straight to the facade client. */
internal class KotlogramUserOperations(private val client: TelegramClient) : FacadeUserOperations {
    override fun users(ids: List<Long>): List<User> = client.usersGetUsers(ids)
}
