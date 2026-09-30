package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Participant as FacadeParticipant
import org.kotlogramme.cli.domain.Participant

/**
 * Reduces a facade participant to the member a terminal client lists.
 *
 * The facade folds every role into one shape and reports [FacadeParticipant.role] as a lowercase
 * name; the domain keeps that name verbatim. The display name reuses the same fallback the message
 * and contact mappers use, and a blank username is dropped rather than rendered blank.
 */
internal fun FacadeParticipant.toParticipant(): Participant = Participant(
    id = user.id,
    displayName = user.displayName(),
    username = user.username?.takeIf(String::isNotBlank),
    role = role,
)
