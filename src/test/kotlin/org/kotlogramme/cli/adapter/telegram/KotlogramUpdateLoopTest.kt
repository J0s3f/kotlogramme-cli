package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.RawUpdate
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TypedUpdate
import com.github.badoualy.telegram.api.UpdateCallback
import com.github.badoualy.telegram.api.UpdatesApi
import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.FakeClock
import org.kotlogramme.cli.domain.IncomingUpdate
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlogramUpdateLoopTest {
    private class RecordingUpdates(var accepts: Boolean = true, var running: Boolean = false) {
        var callback: UpdateCallback? = null
        var stops = 0

        /** An [UpdatesApi] that answers only the loop calls the adapter makes. */
        val api: UpdatesApi = Proxy.newProxyInstance(
            UpdatesApi::class.java.classLoader,
            arrayOf(UpdatesApi::class.java),
        ) { _, method, args ->
            when (method.name) {
                "startUpdateLoop" -> { callback = args[0] as UpdateCallback; accepts }
                "stopUpdateLoop" -> { stops++; null }
                "isUpdateLoopRunning" -> running
                else -> error("unexpected call ${method.name}")
            }
        } as UpdatesApi

        fun deliver(update: TypedUpdate) {
            val client = Proxy.newProxyInstance(
                TelegramClient::class.java.classLoader,
                arrayOf(TelegramClient::class.java),
            ) { _, _, _ -> error("the adapter must not use the client") } as TelegramClient
            requireNotNull(callback).onUpdate(client, update)
        }
    }

    private class CountingUsers(private val known: List<User>) : FacadeUserOperations {
        var lookups = 0

        override fun users(ids: List<Long>): List<User> {
            lookups++
            return known.filter { it.id in ids }
        }
    }

    private val updates = RecordingUpdates()
    private val users = CountingUsers(listOf(user(id = 291049397, firstName = "Ada")))
    private val names = UserNameCache(users, FakeClock(), Duration.ofHours(3))
    private val loop = KotlogramUpdateLoop(updates.api, names) { 42L }
    private val received = mutableListOf<IncomingUpdate>()

    private fun userChanged() = TypedUpdate(
        kind = "raw",
        rawUpdate = RawUpdate("updateUser", HexFormat.of().parseHex("38945220" + "b50f591100000000")),
    )

    @Test
    fun `starting the loop registers a callback with the facade and reports whether it started`() {
        assertTrue(loop.start { received += it })
        assertNotNull(updates.callback)

        updates.accepts = false
        assertFalse(loop.start { received += it })
    }

    @Test
    fun `a message update reaches the callback as a new message`() {
        loop.start { received += it }

        updates.deliver(TypedUpdate(kind = "newMessage", message = message(id = 5, text = "hello")))

        assertEquals("hello", assertIs<IncomingUpdate.NewMessage>(received.single()).message.text)
    }

    @Test
    fun `any other update reaches the callback with its decoded payload`() {
        loop.start { received += it }

        updates.deliver(userChanged())

        val other = assertIs<IncomingUpdate.Other>(received.single())
        assertEquals("updateUser", other.kind)
        assertEquals(291049397L, other.userId)
    }

    @Test
    fun `a user the account is told changed is looked up again`() {
        loop.start { received += it }
        names.nameOf(291049397)
        names.nameOf(291049397)
        assertEquals(1, users.lookups)

        updates.deliver(userChanged())
        names.nameOf(291049397)

        assertEquals(2, users.lookups)
    }

    @Test
    fun `stopping the loop stops the facade's loop`() {
        loop.stop()

        assertEquals(1, updates.stops)
    }

    @Test
    fun `the loop is running when the facade's is`() {
        assertFalse(loop.isRunning)

        updates.running = true

        assertTrue(loop.isRunning)
    }
}
