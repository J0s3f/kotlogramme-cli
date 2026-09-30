package org.kotlogramme.cli.application.port.api

import java.nio.file.Path

/** Download the media attached to a message. */
interface DownloadMedia {
    /** Writes the media of [messageId] in [reference] to [target] and returns the path written. */
    fun download(reference: String, messageId: Int, target: Path): Path
}
