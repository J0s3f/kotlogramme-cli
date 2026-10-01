package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ProfilePhoto
import org.kotlogramme.cli.domain.Photo

/** Maps a facade profile photo to the domain photo a terminal lists. */
internal fun ProfilePhoto.toPhoto(): Photo = Photo(
    id = id,
    dcId = dcId,
    sizeBytes = size,
    width = width,
    height = height,
    spoiler = spoiler,
)
