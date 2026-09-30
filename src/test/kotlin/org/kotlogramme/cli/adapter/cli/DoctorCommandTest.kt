package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.adapter.telegram.NativeLibraryCheck
import org.kotlogramme.cli.adapter.telegram.NativeLibraryProbe
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import org.kotlogramme.cli.application.port.spi.AppConfig
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoctorCommandTest {
    @Test
    fun `doctor reports the native library as loaded`() {
        val fixture = cliFixture(
            config = AppConfig(ApiCredentials(7, "hash"), Paths.get("session.sqlite")),
            nativeLibraryProbe = NativeLibraryProbe { _, _ ->
                NativeLibraryCheck.Loaded("build/libs/kotlogramme-all.jar")
            },
        )

        val result = fixture.run("doctor")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "Doctor: checking the local installation.",
                "Credentials: set (API id 7)",
                "Session path: session.sqlite",
                "Native library: loaded",
                "Facade classes: build/libs/kotlogramme-all.jar",
                "Client: created and closed without a network query.",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `doctor explains how to configure missing credentials`() {
        val fixture = cliFixture(
            config = AppConfig(credentials = null, sessionPath = Paths.get("session.sqlite")),
        )

        val result = fixture.run("doctor")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("TG_API_ID/TG_API_HASH"), "stderr was: ${result.stderr}")
        assertTrue(fixture.output.lines.isEmpty())
    }

    @Test
    fun `doctor turns a missing native library into a clear error`() {
        val fixture = cliFixture(
            nativeLibraryProbe = NativeLibraryProbe { _, _ ->
                NativeLibraryCheck.Unavailable("no native build for this platform")
            },
        )

        val result = fixture.run("doctor")

        assertEquals(1, result.statusCode)
        assertTrue(
            result.stderr.contains("native library could not be loaded"),
            "stderr was: ${result.stderr}",
        )
        assertFalse(result.stderr.contains("UnsatisfiedLinkError"))
    }
}
