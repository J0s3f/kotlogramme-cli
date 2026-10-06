package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.AppConfig
import org.kotlogramme.cli.domain.DiagnosticStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigurationDiagnosticTest {
    private val config = AppConfig(credentials = null, sessionPath = Path.of("session.sqlite"))

    private fun tempDirectory(): Path = Files.createTempDirectory("kotlogramme-config-test")

    @Test
    fun `a writable directory with a readable config is fine`() {
        val dir = tempDirectory()

        val finding = ConfigurationDiagnostic(dir) { config }.run()

        assertEquals(DiagnosticStatus.OK, finding.status, finding.detail)
        assertTrue(finding.detail.contains(dir.toString()), finding.detail)
    }

    @Test
    fun `leaves nothing behind after probing the directory`() {
        val dir = tempDirectory()

        ConfigurationDiagnostic(dir) { config }.run()

        assertEquals(0, Files.list(dir).use { it.count() })
    }

    @Test
    fun `a config that cannot be read is failed with the reason`() {
        val finding = ConfigurationDiagnostic(tempDirectory()) { error("Unexpected token at config.json:3") }.run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("Unexpected token"), finding.detail)
    }

    @Test
    fun `a directory that cannot be written is failed`() {
        val file = Files.createTempFile("kotlogramme-config-test", ".txt")

        val finding = ConfigurationDiagnostic(file) { config }.run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("not writable"), finding.detail)
    }

    @Test
    fun `a directory that does not exist yet is fine when it can be created`() {
        val dir = tempDirectory().resolve("nested").resolve("kotlogramme")

        val finding = ConfigurationDiagnostic(dir) { config }.run()

        assertEquals(DiagnosticStatus.OK, finding.status, finding.detail)
        assertTrue(finding.detail.contains("created on first use"), finding.detail)
    }

    @Test
    fun `checking a directory that does not exist yet does not create it`() {
        val dir = tempDirectory().resolve("kotlogramme")

        ConfigurationDiagnostic(dir) { config }.run()

        assertTrue(Files.notExists(dir))
    }
}
