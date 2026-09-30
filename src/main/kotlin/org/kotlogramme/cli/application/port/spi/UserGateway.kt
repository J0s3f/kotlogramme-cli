package org.kotlogramme.cli.application.port.spi

/**
 * Resolves numeric user ids to usernames.
 *
 * A message carries its inline bot only as a numeric [org.kotlogramme.cli.domain.Message.viaBotId];
 * this is the outbound port that turns those ids into the `@username` the renderer shows. The
 * lookup is best-effort: an id Telegram cannot resolve, or a user without a username, is simply
 * absent from the answer, and a failing lookup must not fail the command that asked for it.
 */
interface UserGateway {
    /**
     * The username behind each of [ids], keyed by id. A repeated id is looked up once; an id with
     * no username is absent.
     */
    fun usernames(ids: List<Long>): Map<Long, String>
}
