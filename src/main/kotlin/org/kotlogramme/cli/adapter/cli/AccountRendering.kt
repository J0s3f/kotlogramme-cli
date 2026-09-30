package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Account

/** Prints the account fields a terminal client shows; the API hash never appears here. */
internal fun Output.printAccount(account: Account) {
    line("Signed in as ${account.displayName}")
    line("id: ${account.id}")
    account.username?.takeIf(String::isNotBlank)?.let { line("username: @${it.removePrefix("@")}") }
    account.phoneNumber?.takeIf(String::isNotBlank)?.let { line("phone: $it") }
}
