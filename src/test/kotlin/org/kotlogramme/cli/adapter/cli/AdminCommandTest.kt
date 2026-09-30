package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminCommandTest {
    @Test
    fun `permissions prints every right as granted or denied`() {
        val fake = FakeAdminRights(ChatRights(changeInfo = true, banUsers = true))
        val fixture = cliFixture(adminRights = fake)

        val result = fixture.run("permissions", "@team", "@ada")

        assertEquals(0, result.statusCode)
        assertEquals(AdminRefs("@team", "@ada"), fake.permissionCalls.single())
        assertEquals(
            listOf(
                "right\tstatus",
                "change-info\tgranted",
                "post-messages\tdenied",
                "edit-messages\tdenied",
                "delete-messages\tdenied",
                "ban-users\tgranted",
                "invite-users\tdenied",
                "pin-messages\tdenied",
                "add-admins\tdenied",
                "anonymous\tdenied",
                "manage-call\tdenied",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `promote grants the named comma-separated rights`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        val result = fixture.run("promote", "@team", "@ada", "--grant", "change-info, ban-users")

        assertEquals(0, result.statusCode)
        assertEquals(
            PromoteCall("@team", "@ada", ChatRights(changeInfo = true, banUsers = true)),
            fake.promotions.single(),
        )
        assertEquals(listOf("Promoted @ada in @team."), fixture.output.lines)
    }

    @Test
    fun `promote with neither option grants nothing`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        fixture.run("promote", "@team", "@ada")

        assertEquals(PromoteCall("@team", "@ada", ChatRights.NONE), fake.promotions.single())
    }

    @Test
    fun `promote --all grants every right`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        fixture.run("promote", "@team", "@ada", "--all")

        assertEquals(PromoteCall("@team", "@ada", ChatRights.ALL), fake.promotions.single())
    }

    @Test
    fun `promote rejects an unknown right name`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        val result = fixture.run("promote", "@team", "@ada", "--grant", "change-info,bogus")

        assertTrue(result.statusCode != 0)
        assertTrue(result.stderr.contains("unknown right bogus"), "stderr was: ${result.stderr}")
        assertEquals(emptyList(), fake.promotions)
    }

    @Test
    fun `restrict bans by default with a finite expiry`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)
        val before = Instant.now()

        val result = fixture.run("restrict", "@team", "@ada")

        assertEquals(0, result.statusCode)
        val applied = fake.restrictions.single().restrictions
        assertEquals(ChatRestrictions.NONE_ALLOWED, applied.copy(untilDate = null))
        val until = assertNotNull(applied.untilDate)
        assertTrue(until.isAfter(before), "expiry $until was not after $before")
        assertTrue(until.isBefore(before.plus(Duration.ofHours(25))), "expiry $until was not finite")
    }

    @Test
    fun `restrict --forever lifts the expiry`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        fixture.run("restrict", "@team", "@ada", "--forever")

        val applied = fake.restrictions.single().restrictions
        assertEquals(ChatRestrictions.NONE_ALLOWED, applied)
        assertEquals(null, applied.untilDate)
        assertEquals(listOf("Restricted @ada in @team, forever."), fixture.output.lines)
    }

    @Test
    fun `restrict --allow keeps the named abilities only`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        fixture.run("restrict", "@team", "@ada", "--allow", "view-messages,send-messages")

        val applied = fake.restrictions.single().restrictions
        assertTrue(applied.viewMessages)
        assertTrue(applied.sendMessages)
        assertFalse(applied.sendMedia)
        assertFalse(applied.pinMessages)
        assertNotNull(applied.untilDate)
    }

    @Test
    fun `restrict rejects an unknown ability name`() {
        val fake = FakeAdminRights()
        val fixture = cliFixture(adminRights = fake)

        val result = fixture.run("restrict", "@team", "@ada", "--allow", "post-messages")

        assertTrue(result.statusCode != 0)
        assertTrue(result.stderr.contains("unknown right post-messages"), "stderr was: ${result.stderr}")
        assertEquals(emptyList(), fake.restrictions)
    }
}
