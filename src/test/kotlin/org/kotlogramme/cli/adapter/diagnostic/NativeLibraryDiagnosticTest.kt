package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.adapter.telegram.NativeLibraryCheck
import org.kotlogramme.cli.adapter.telegram.NativeLibraryProbe
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.domain.DiagnosticStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeLibraryDiagnosticTest {
    @Test
    fun `reports a loaded library`() {
        val probe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Loaded }

        val finding = NativeLibraryDiagnostic(probe).run()

        assertEquals(DiagnosticStatus.OK, finding.status)
        assertTrue(finding.detail.contains("loaded"), finding.detail)
    }

    @Test
    fun `stays short enough for a table even with a long library path`() {
        val probe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Loaded }
        val path = "C:/Users/someone/AppData/Local/Temp/kotlogramme-windows-x86_64/4saFP4W9/kotlogramme.dll"

        val finding = NativeLibraryDiagnostic(probe, loadedFrom = { path }).run()

        assertTrue(finding.detail.length <= 110, "${finding.detail.length} characters: ${finding.detail}")
    }

    @Test
    fun `says the library was loaded from beside the executable when it was`() {
        val probe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Loaded }

        val finding = NativeLibraryDiagnostic(probe, loadedFrom = { "C:/cache/kotlogramme.dll" }).run()

        assertTrue(finding.detail.contains("from C:/cache/kotlogramme.dll"), finding.detail)
    }

    @Test
    fun `says the library was loaded from the bundled copy when no path was set`() {
        val probe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Loaded }

        val finding = NativeLibraryDiagnostic(probe, loadedFrom = { null }).run()

        assertTrue(finding.detail.contains("bundled copy"), finding.detail)
    }

    @Test
    fun `reports a library that cannot be loaded as failed with the reason`() {
        val probe = NativeLibraryProbe { _, _ -> NativeLibraryCheck.Unavailable("no native build for this platform") }

        val finding = NativeLibraryDiagnostic(probe).run()

        assertEquals(DiagnosticStatus.FAILED, finding.status)
        assertTrue(finding.detail.contains("no native build for this platform"), finding.detail)
    }

    @Test
    fun `needs no credentials and never touches the real session`() {
        var seenCredentials: ApiCredentials? = null
        var seenSession: Path? = null
        val probe = NativeLibraryProbe { credentials, session ->
            seenCredentials = credentials
            seenSession = session
            NativeLibraryCheck.Loaded
        }

        NativeLibraryDiagnostic(probe).run()

        assertNotNull(seenCredentials)
        assertTrue(seenSession.toString().contains("kotlogramme-doctor"), seenSession.toString())
    }

    @Test
    fun `removes the scratch session directory afterwards`() {
        var session: Path? = null
        val probe = NativeLibraryProbe { _, path ->
            session = path
            Files.writeString(path, "x")
            NativeLibraryCheck.Loaded
        }

        NativeLibraryDiagnostic(probe).run()

        assertFalse(Files.exists(session!!.parent), "scratch directory is still there")
    }

    @Test
    fun `removes the scratch session directory even when the probe throws`() {
        var session: Path? = null
        val probe = NativeLibraryProbe { _, path ->
            session = path
            error("boom")
        }

        runCatching { NativeLibraryDiagnostic(probe).run() }

        assertFalse(Files.exists(session!!.parent), "scratch directory is still there")
    }
}
