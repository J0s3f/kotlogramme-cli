package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AccountAuthorization
import com.github.badoualy.telegram.api.Authorizations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlogramSessionGatewayTest {
    @Test
    fun `sessions maps every authorization in order`() {
        val operations = FakeSessionOperations().apply {
            answer = Authorizations(
                authorizationTtlDays = 180,
                authorizations = listOf(
                    authorization(hash = 11, device = "Desktop", current = true),
                    authorization(hash = 22, device = "Phone", current = false),
                ),
            )
        }

        val sessions = KotlogramSessionGateway(operations).sessions()

        assertEquals(listOf(11L, 22L), sessions.map { it.hash })
        assertEquals(listOf("Desktop", "Phone"), sessions.map { it.deviceModel })
        assertTrue(sessions.first().current)
        assertFalse(sessions.last().current)
        assertEquals(1_700_000_000_000, sessions.first().createdAt.toEpochMilli())
    }

    @Test
    fun `device falls back to the platform when the model is blank`() {
        val operations = FakeSessionOperations().apply {
            answer = Authorizations(180, listOf(authorization(hash = 1, device = "", platform = "Android")))
        }

        val session = KotlogramSessionGateway(operations).sessions().single()

        assertEquals("Android", session.device)
    }

    @Test
    fun `terminate forwards the hash`() {
        val operations = FakeSessionOperations()

        KotlogramSessionGateway(operations).terminate(42L)

        assertEquals(listOf(42L), operations.terminated)
    }

    @Test
    fun `terminateAll resets every other session`() {
        val operations = FakeSessionOperations()

        KotlogramSessionGateway(operations).terminateAll()

        assertEquals(1, operations.resetAllCount)
    }
}

private fun authorization(
    hash: Long,
    device: String = "Desktop",
    platform: String = "Windows",
    current: Boolean = false,
): AccountAuthorization = AccountAuthorization(
    hash = hash,
    deviceModel = device,
    platform = platform,
    systemVersion = "11",
    apiId = 1,
    appName = "kotlogramme",
    appVersion = "0.4.0",
    dateCreated = 1_700_000_000_000,
    dateActive = 1_700_000_100_000,
    ip = "203.0.113.7",
    country = "AT",
    region = "Vienna",
    current = current,
)

private class FakeSessionOperations : FacadeSessionOperations {
    var answer: Authorizations = Authorizations(180, emptyList())
    val terminated = mutableListOf<Long>()
    var resetAllCount = 0

    override fun authorizations(): Authorizations = answer

    override fun resetAuthorization(hash: Long) {
        terminated += hash
    }

    override fun resetAuthorizations() {
        resetAllCount++
    }
}
