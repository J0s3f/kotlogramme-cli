package org.kotlogramme.cli.adapter.telegram

import java.nio.file.Files
import java.nio.file.Path

/**
 * Points the facade at a native library that sits beside the running executable.
 *
 * The facade otherwise copies its library out of the classpath into a fresh temporary directory on
 * every start. A native executable that ships the library as a file next to it, which the single-file
 * packaging unpacks once and keeps, needs no copy at all: the facade loads the file named by the
 * `kotlogramme.native.path` property. On a JVM the executable is `java`, no library sits beside it,
 * and the facade keeps loading the copy bundled in the jar.
 */
internal class SidecarNativeLibrary(
    private val executable: () -> Path? = ::runningExecutable,
    private val libraryFileName: String = DEFAULT_FILE_NAME,
    private val getProperty: (String) -> String? = System::getProperty,
    private val setProperty: (String, String) -> Unit = { key, value -> System.setProperty(key, value) },
) {
    /** The library beside the executable, or `null` when there is none or the executable is unknown. */
    fun locate(): Path? = executable()?.resolveSibling(libraryFileName)?.takeIf(Files::isRegularFile)

    /**
     * Tells the facade to load the library found by [locate] and returns it, or does nothing and
     * returns `null` when a path was already configured or there is no library to use. A path the
     * user set explicitly always wins.
     */
    fun register(): Path? {
        if (!getProperty(PATH_PROPERTY).isNullOrBlank()) return null
        return locate()?.also { setProperty(PATH_PROPERTY, it.toAbsolutePath().toString()) }
    }

    companion object {
        /** The property the facade reads to load a library from a given file. */
        const val PATH_PROPERTY = "kotlogramme.native.path"

        val DEFAULT_FILE_NAME: String = System.mapLibraryName("kotlogramme")
    }
}

private fun runningExecutable(): Path? = ProcessHandle.current().info().command().map(Path::of).orElse(null)
