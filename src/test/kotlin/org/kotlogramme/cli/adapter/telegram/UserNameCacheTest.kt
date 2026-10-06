package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.FakeClock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserNameCacheTest {
    private class FakeUsers(
        private var known: List<User>,
        var failing: Boolean = false,
    ) : FacadeUserOperations {
        var lookups = 0

        fun rename(user: User) {
            known = known.filter { it.id != user.id } + user
        }

        override fun users(ids: List<Long>): List<User> {
            lookups++
            check(!failing) { "Telegram is unreachable" }
            return known.filter { it.id in ids }
        }
    }

    private val clock = FakeClock()
    private val lifetime = Duration.ofHours(3)

    private fun cacheOf(users: FakeUsers) = UserNameCache(users, clock, lifetime)

    @Test
    fun `names a user by first and last name`() {
        val cache = cacheOf(FakeUsers(listOf(user(id = 7, firstName = "Ada", lastName = "Lovelace"))))

        assertEquals("Ada Lovelace", cache.nameOf(7))
    }

    @Test
    fun `asks Telegram once for an id it already resolved`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)

        cache.nameOf(7)
        cache.nameOf(7)

        assertEquals(1, users.lookups)
    }

    @Test
    fun `keeps a name until just before its lifetime ends`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)

        cache.nameOf(7)
        clock.advance(lifetime.minusNanos(1))
        cache.nameOf(7)

        assertEquals(1, users.lookups)
    }

    @Test
    fun `asks Telegram again once a name outlives its lifetime`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)

        cache.nameOf(7)
        clock.advance(lifetime)
        cache.nameOf(7)

        assertEquals(2, users.lookups)
    }

    @Test
    fun `shows the new name of a user renamed after the lifetime ended`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)
        cache.nameOf(7)

        users.rename(user(id = 7, firstName = "Augusta"))
        clock.advance(lifetime)

        assertEquals("Augusta", cache.nameOf(7))
    }

    @Test
    fun `keeps showing the old name until the lifetime ends`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)
        cache.nameOf(7)

        users.rename(user(id = 7, firstName = "Augusta"))
        clock.advance(Duration.ofHours(1))

        assertEquals("Ada", cache.nameOf(7))
    }

    @Test
    fun `an expired name is not served when the lookup then fails`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)
        cache.nameOf(7)

        users.failing = true
        clock.advance(lifetime)

        assertNull(cache.nameOf(7))
    }

    @Test
    fun `expires each id on its own schedule`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada"), user(id = 8, firstName = "Bob")))
        val cache = cacheOf(users)
        cache.nameOf(7)
        clock.advance(Duration.ofHours(2))
        cache.nameOf(8)
        users.lookups = 0

        clock.advance(Duration.ofHours(1))
        cache.nameOf(7)
        cache.nameOf(8)

        assertEquals(1, users.lookups)
    }

    @Test
    fun `forgets a name so the next reading asks Telegram again`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")))
        val cache = cacheOf(users)
        cache.nameOf(7)

        users.rename(user(id = 7, firstName = "Augusta"))
        cache.forget(7)

        assertEquals("Augusta", cache.nameOf(7))
    }

    @Test
    fun `forgetting one id leaves the others cached`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada"), user(id = 8, firstName = "Bob")))
        val cache = cacheOf(users)
        cache.nameOf(7)
        cache.nameOf(8)
        users.lookups = 0

        cache.forget(7)
        cache.nameOf(8)

        assertEquals(0, users.lookups)
    }

    @Test
    fun `forgetting an id that was never cached is harmless`() {
        cacheOf(FakeUsers(emptyList())).forget(5)
    }

    @Test
    fun `has no name for an id Telegram does not resolve`() {
        assertNull(cacheOf(FakeUsers(emptyList())).nameOf(99))
    }

    @Test
    fun `asks again for an id Telegram did not resolve`() {
        val users = FakeUsers(emptyList())
        val cache = cacheOf(users)

        cache.nameOf(99)
        cache.nameOf(99)

        assertEquals(2, users.lookups)
    }

    @Test
    fun `a failing lookup yields no name instead of ending the stream`() {
        assertNull(cacheOf(FakeUsers(emptyList(), failing = true)).nameOf(7))
    }

    @Test
    fun `recovers once a failing lookup works again`() {
        val users = FakeUsers(listOf(user(id = 7, firstName = "Ada")), failing = true)
        val cache = cacheOf(users)
        cache.nameOf(7)

        users.failing = false

        assertEquals("Ada", cache.nameOf(7))
    }
}
