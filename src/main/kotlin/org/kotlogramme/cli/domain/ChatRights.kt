package org.kotlogramme.cli.domain

/**
 * The admin rights a promoted member can hold, mirroring the facade's `ChatPermissions`.
 *
 * Every flag defaults to false: a promotion grants exactly what it names.
 */
data class ChatRights(
    val changeInfo: Boolean = false,
    val postMessages: Boolean = false,
    val editMessages: Boolean = false,
    val deleteMessages: Boolean = false,
    val banUsers: Boolean = false,
    val inviteUsers: Boolean = false,
    val pinMessages: Boolean = false,
    val addAdmins: Boolean = false,
    val anonymous: Boolean = false,
    val manageCall: Boolean = false,
) {
    companion object {
        val NONE: ChatRights = ChatRights()

        val ALL: ChatRights = ChatRights(
            changeInfo = true,
            postMessages = true,
            editMessages = true,
            deleteMessages = true,
            banUsers = true,
            inviteUsers = true,
            pinMessages = true,
            addAdmins = true,
            anonymous = true,
            manageCall = true,
        )
    }
}
