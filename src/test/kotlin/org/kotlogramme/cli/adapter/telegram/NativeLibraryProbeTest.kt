package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.TelegramClient
import org.junit.jupiter.api.io.TempDir
import org.kotlogramme.cli.application.port.spi.ApiCredentials
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NativeLibraryProbeTest {
    @field:TempDir
    private lateinit var tempDir: Path

    private val credentials = ApiCredentials(apiId = 1, apiHash = "hash")

    private val closedClients = mutableListOf<String>()

    private fun fakeClient(): TelegramClient = Proxy.newProxyInstance(
        TelegramClient::class.java.classLoader,
        arrayOf(TelegramClient::class.java),
    ) { _, method, _ ->
        if (method.name == "close") closedClients += "closed" else error("unexpected call ${method.name}")
        null
    } as TelegramClient

    private fun check(create: (ApiCredentials, Path) -> TelegramClient) =
        FacadeNativeLibraryProbe(create).check(credentials, tempDir.resolve("session.sqlite"))

    @Test
    fun `a client that can be built means the library loaded`() {
        assertEquals(NativeLibraryCheck.Loaded, check { _, _ -> fakeClient() })
    }

    @Test
    fun `the client is closed again without being used`() {
        check { _, _ -> fakeClient() }

        assertEquals(1, closedClients.size)
    }

    @Test
    fun `the credentials and session reach the client factory`() {
        var received: Pair<ApiCredentials, Path>? = null

        check { given, session -> received = given to session; fakeClient() }

        assertEquals(credentials to tempDir.resolve("session.sqlite"), received)
    }

    @Test
    fun `a library that cannot be linked is unavailable with the reason`() {
        val result = check { _, _ -> throw UnsatisfiedLinkError("no kotlogramme in java.library.path") }

        assertEquals(NativeLibraryCheck.Unavailable("no kotlogramme in java.library.path"), result)
    }

    @Test
    fun `the first message in the chain of causes is the reason`() {
        val result = check { _, _ -> throw LinkageError(null, RuntimeException("wrong ELF class")) }

        assertEquals(NativeLibraryCheck.Unavailable("wrong ELF class"), result)
    }

    @Test
    fun `an error with no message anywhere is described by its type`() {
        val result = assertIs<NativeLibraryCheck.Unavailable>(check { _, _ -> throw UnsatisfiedLinkError() })

        assertEquals("java.lang.UnsatisfiedLinkError", result.reason)
    }

    @Test
    fun `an error that is not a linkage error is not swallowed`() {
        assertFailsWith<IllegalStateException> { check { _, _ -> error("disk full") } }
    }

    @Test
    fun `the real facade client builds offline and so the real library loads`() {
        assertEquals(NativeLibraryCheck.Loaded, FacadeNativeLibraryProbe().check(credentials, tempDir.resolve("s.sqlite")))
    }
}
