package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.domain.DiagnosticStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeDiagnosticTest {
    private val jvm = mapOf(
        "java.version" to "25.0.4",
        "java.vendor" to "Eclipse Adoptium",
        "os.name" to "Windows 11",
        "os.arch" to "amd64",
    )

    @Test
    fun `describes the version, java and platform`() {
        val finding = RuntimeDiagnostic("0.5.1", jvm::get).run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("kotlogramme 0.5.1"), finding.detail)
        assertTrue(finding.detail.contains("Java 25.0.4 (Eclipse Adoptium)"), finding.detail)
        assertTrue(finding.detail.contains("Windows 11 amd64"), finding.detail)
    }

    @Test
    fun `says it runs on a JVM`() {
        assertTrue(RuntimeDiagnostic("0.5.1", jvm::get).run().detail.endsWith("JVM"))
    }

    @Test
    fun `says it runs as a native image`() {
        val native = jvm + ("org.graalvm.nativeimage.imagecode" to "runtime")

        assertTrue(RuntimeDiagnostic("0.5.1", native::get).run().detail.endsWith("native image"))
    }

    @Test
    fun `survives properties the runtime does not set`() {
        val finding = RuntimeDiagnostic("0.5.1") { null }.run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("unknown"), finding.detail)
    }
}
