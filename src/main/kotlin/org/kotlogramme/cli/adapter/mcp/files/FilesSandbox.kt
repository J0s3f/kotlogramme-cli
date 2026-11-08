package org.kotlogramme.cli.adapter.mcp.files

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * The one directory the file tools may touch.
 *
 * A path that a model supplies is a way to read any file the process can read, so the tools never take a
 * path as the operating system understands it. They take a name relative to this directory, in the
 * portable form `folder/file.ext`, and this class decides what it means:
 *
 * - reading ([existingFile]) refuses absolute, drive-relative and UNC paths, `..` segments, backslashes,
 *   colons and Windows device names before the file system is asked, then resolves the real path, which
 *   follows symbolic links and junctions, and checks that the result is still inside the directory;
 * - writing ([newFile]) takes a bare file name, never creates anything outside the directory and never
 *   replaces an existing file.
 *
 * A file swapped for a link between the check and the use is not defended against: the directory is one the
 * user chose for this purpose, and nothing here can lock it.
 */
class FilesSandbox private constructor(private val root: Path) {
    /** The real path of the directory, for messages that tell the user where files land. */
    val directory: Path get() = root

    /** The file [relative] names, which must be a regular file inside the directory of at most [MAX_UPLOAD_BYTES]. */
    fun existingFile(relative: String): Path {
        val segments = segmentsOf(relative)
        val real = realPathOf(root.resolve(segments.joinToString("/")), relative)
        require(real.startsWith(root)) { "'$relative' leads outside the files directory." }
        require(Files.isRegularFile(real)) { "'$relative' is not a file." }
        val size = Files.size(real)
        require(size <= MAX_UPLOAD_BYTES) { "'$relative' is larger than ${MAX_UPLOAD_BYTES / BYTES_PER_MEBIBYTE} MiB." }
        return real
    }

    /** The path of a file called [name] that does not exist yet, directly inside the directory. */
    fun newFile(name: String): Path {
        require(isPlainFileName(name)) {
            "'$name' is not a usable file name: use a plain name without folders, colons, a trailing dot " +
                "or space, or a Windows device name."
        }
        val target = root.resolve(name)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            "'$name' already exists in the files directory; choose another file_name."
        }
        return target
    }

    /** The name of [path] relative to the directory, in the portable form the tools use. */
    fun relativeName(path: Path): String = root.relativize(path.toRealPath()).joinToString("/")

    private fun segmentsOf(relative: String): List<String> {
        require(relative.isNotBlank()) { "The file name must not be blank." }
        require(relative.length <= MAX_PATH_LENGTH) { "The file name is longer than $MAX_PATH_LENGTH characters." }
        require(relative.none(Char::isISOControl)) { "The file name must not contain control characters." }
        require('\\' !in relative && ':' !in relative) {
            "Use forward slashes and no colons: write 'folder/file.ext'."
        }
        require(!relative.startsWith("/")) { "Give a path relative to the files directory, not an absolute one." }
        val segments = relative.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." }) {
            "The path must not contain empty, '.' or '..' parts."
        }
        require(segments.all { isPlainFileName(it) }) { "'$relative' is not a usable path." }
        return segments
    }

    private fun realPathOf(candidate: Path, relative: String): Path =
        try {
            candidate.toRealPath()
        } catch (_: NoSuchFileException) {
            throw IllegalArgumentException("There is no file '$relative' in the files directory.")
        } catch (_: IOException) {
            throw IllegalArgumentException("'$relative' cannot be read.")
        }

    companion object {
        /** Telegram takes much larger files; a model-driven upload gets a modest cap instead. */
        const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024

        private const val BYTES_PER_MEBIBYTE = 1024 * 1024
        private const val MAX_PATH_LENGTH = 1024
        private const val MAX_NAME_LENGTH = 255

        private val DEVICE_NAMES = Regex("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?")

        /** Opens the sandbox on [directory], which must be an existing directory. */
        fun on(directory: Path): FilesSandbox {
            require(Files.isDirectory(directory)) { "The files directory '$directory' is not a directory." }
            return FilesSandbox(directory.toRealPath())
        }

        /** Whether [name] is one segment that every supported operating system accepts as a file name. */
        fun isPlainFileName(name: String): Boolean =
            name.isNotEmpty() &&
                name.length <= MAX_NAME_LENGTH &&
                name != "." && name != ".." &&
                name.none { it.isISOControl() || it in FORBIDDEN_CHARACTERS } &&
                !name.endsWith(".") && !name.endsWith(" ") && !name.startsWith(" ") &&
                !DEVICE_NAMES.matches(name)

        private const val FORBIDDEN_CHARACTERS = "/\\:*?\"<>|"
    }
}
