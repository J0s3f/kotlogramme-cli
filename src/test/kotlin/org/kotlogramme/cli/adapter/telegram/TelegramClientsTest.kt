package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.ApiCredentials
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TelegramClientsTest {
    private class FakeClient : AutoCloseable {
        var closed = 0

        override fun close() {
            closed++
        }
    }

    private val created = mutableListOf<FakeClient>()
    private val clients = TelegramClients<FakeClient> { _, _ -> FakeClient().also(created::add) }
    private val credentials = ApiCredentials(apiId = 1, apiHash = "hash")
    private val session: Path = Paths.get("session.sqlite")

    @Test
    fun `by default every request builds its own client, as the one-shot commands always did`() {
        val first = clients.clientFor(credentials, session)
        val second = clients.clientFor(credentials, session)

        assertNotSame(first, second)
        assertEquals(2, created.size)
    }

    @Test
    fun `after reuse is switched on the same client is handed out`() {
        clients.reuseOneClient()

        val first = clients.clientFor(credentials, session)
        val second = clients.clientFor(credentials, session)

        assertSame(first, second)
        assertEquals(1, created.size)
    }

    @Test
    fun `a different session gets a different client and the old one is closed`() {
        clients.reuseOneClient()
        val first = clients.clientFor(credentials, session)

        val second = clients.clientFor(credentials, Paths.get("other.sqlite"))

        assertNotSame(first, second)
        assertEquals(1, first.closed)
    }

    @Test
    fun `different credentials get a different client`() {
        clients.reuseOneClient()
        val first = clients.clientFor(credentials, session)

        val second = clients.clientFor(ApiCredentials(apiId = 2, apiHash = "hash"), session)

        assertNotSame(first, second)
    }

    @Test
    fun `closing closes the shared client once and a later request builds a new one`() {
        clients.reuseOneClient()
        val first = clients.clientFor(credentials, session)

        clients.close()
        clients.close()
        val second = clients.clientFor(credentials, session)

        assertEquals(1, first.closed)
        assertNotSame(first, second)
    }

    @Test
    fun `closing without reuse closes nothing because the caller owns those clients`() {
        val client = clients.clientFor(credentials, session)

        clients.close()

        assertEquals(0, client.closed)
        assertTrue(created.size == 1)
    }
}
