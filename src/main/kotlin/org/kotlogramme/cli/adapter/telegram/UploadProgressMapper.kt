package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.UploadProgress as FacadeProgress
import org.kotlogramme.cli.application.port.spi.UploadProgress

/**
 * The facade's upload counter in domain terms.
 *
 * The facade carries its own rate, but the domain derives that from the bytes and the elapsed time
 * instead, so every reader of a transfer measures it the same way and a fake can state a rate without
 * inventing one.
 */
internal fun FacadeProgress.toUploadProgress(): UploadProgress = UploadProgress(
    bytesSent = sent,
    totalBytes = total,
    elapsedMillis = elapsedMillis,
)
