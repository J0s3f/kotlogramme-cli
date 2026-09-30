package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DialogFolder
import com.github.badoualy.telegram.api.TelegramClient

/** The real [FacadeFolderOperations], delegating straight to the facade client. */
internal class KotlogramFolderOperations(private val client: TelegramClient) : FacadeFolderOperations {
    override fun folders(): List<DialogFolder> = client.messagesGetDialogFilters().filters
}
