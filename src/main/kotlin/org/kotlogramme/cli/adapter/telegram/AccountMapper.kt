package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.domain.Account

/** Reduces a facade [User] to the fields a terminal client shows. */
internal fun User.toAccount(): Account = Account(
    id = id,
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty(),
    username = username,
    phoneNumber = phone,
)
