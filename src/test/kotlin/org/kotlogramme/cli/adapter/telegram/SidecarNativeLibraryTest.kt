package org.kotlogramme.cli.adapter.telegram

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidecarNativeLibraryTest {
    private val directory: Path = Files.createTempDirectory("kotlogramme-sidecar-test")
    private val executable: Path = Files.createFile(directory.resolve("kotlogramme-test.exe"))
    private val properties = mutableMapOf<String, String>()

    private fun sidecar(
        executable: Path? = this.executable,
        fileName: String = "kotlogramme.dll",
    ) = SidecarNativeLibrary(
        executable = { executable },
        libraryFileName = fileName,
        getProperty = properties::get,
        setProperty = properties::put,
    )

    @Test
    fun `finds the library next to the executable`() {
        val library = Files.createFile(directory.resolve("kotlogramme.dll"))

        assertEquals(library, sidecar().locate())
    }

    @Test
    fun `finds nothing when the library is not beside the executable`() {
        assertNull(sidecar().locate())
    }

    @Test
    fun `finds nothing when the executable cannot be determined`() {
        Files.createFile(directory.resolve("kotlogramme.dll"))

        assertNull(sidecar(executable = null).locate())
    }

    @Test
    fun `a directory with the library's name is not mistaken for it`() {
        Files.createDirectory(directory.resolve("kotlogramme.dll"))

        assertNull(sidecar().locate())
    }

    @Test
    fun `looks for the file name it was given`() {
        val library = Files.createFile(directory.resolve("libkotlogramme.so"))

        assertEquals(library, sidecar(fileName = "libkotlogramme.so").locate())
    }

    @Test
    fun `registering points the facade at the library by its absolute path`() {
        val library = Files.createFile(directory.resolve("kotlogramme.dll"))

        val registered = sidecar().register()

        assertEquals(library, registered)
        assertEquals(library.toAbsolutePath().toString(), properties["kotlogramme.native.path"])
    }

    @Test
    fun `registering leaves a path the user configured alone`() {
        Files.createFile(directory.resolve("kotlogramme.dll"))
        properties["kotlogramme.native.path"] = "C:/custom/kotlogramme.dll"

        val registered = sidecar().register()

        assertNull(registered)
        assertEquals("C:/custom/kotlogramme.dll", properties["kotlogramme.native.path"])
    }

    @Test
    fun `registering sets nothing when there is no library to point at`() {
        val registered = sidecar().register()

        assertNull(registered)
        assertEquals(emptyMap(), properties)
    }

    @Test
    fun `a blank configured path does not count as configured`() {
        val library = Files.createFile(directory.resolve("kotlogramme.dll"))
        properties["kotlogramme.native.path"] = " "

        assertEquals(library, sidecar().register())
    }

    @Test
    fun `the default file name is the platform's name for the library`() {
        assertEquals(System.mapLibraryName("kotlogramme"), SidecarNativeLibrary.DEFAULT_FILE_NAME)
    }
}
