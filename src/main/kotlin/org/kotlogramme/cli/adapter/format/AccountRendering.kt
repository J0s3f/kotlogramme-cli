package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Account

/** Reports the account fields a client shows; the API hash never appears here. */
fun Output.renderAccount(account: Account) {
    val fields = buildList {
        add("id" to account.id.toString())
        account.username?.takeIf(String::isNotBlank)?.let { add("username" to "@${it.removePrefix("@")}") }
        account.phoneNumber?.takeIf(String::isNotBlank)?.let { add("phone" to it) }
    }
    details("Signed in as ${account.displayName}", fields)
}
