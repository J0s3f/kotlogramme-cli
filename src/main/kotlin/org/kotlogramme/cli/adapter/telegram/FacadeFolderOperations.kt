package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DialogFolder

/**
 * The facade folder call the folder gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramFolderGateway] can be exercised without a live client. The facade's
 * `messagesGetDialogFilters` answer carries the account's tags flag too, but the gateway only needs
 * the filters themselves.
 */
internal interface FacadeFolderOperations {
    /** The account's dialog filters, which is `messagesGetDialogFilters`. */
    fun folders(): List<DialogFolder>
}
