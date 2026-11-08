package org.kotlogramme.cli.adapter.mcp.files

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The traversal matrix of the plan: every way a model-written path could leave the files directory. */
class FilesSandboxTest {
    @TempDir
    lateinit var base: Path

    private lateinit var inside: Path
    private lateinit var outside: Path
    private lateinit var sandbox: FilesSandbox

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        inside = Files.createDirectory(base.resolve("files"))
        outside = Files.createDirectory(base.resolve("secrets"))
        Files.writeString(outside.resolve("passwd"), "secret")
        Files.writeString(inside.resolve("a.txt"), "a")
        Files.createDirectory(inside.resolve("sub"))
        Files.writeString(inside.resolve("sub").resolve("b.txt"), "b")
        sandbox = FilesSandbox.on(inside)
    }

    private fun refused(relative: String) {
        assertFailsWith<IllegalArgumentException>(relative) { sandbox.existingFile(relative) }
    }

    // --- reading

    @Test
    fun `a file inside the directory is found, also in a subfolder`() {
        assertEquals(inside.toRealPath().resolve("a.txt"), sandbox.existingFile("a.txt"))
        assertEquals(inside.toRealPath().resolve("sub").resolve("b.txt"), sandbox.existingFile("sub/b.txt"))
    }

    @Test
    fun `parent segments are refused wherever they stand`() {
        listOf("../secrets/passwd", "sub/../../secrets/passwd", "..", "sub/..", "./a.txt", "sub/./b.txt").forEach(::refused)
    }

    @Test
    fun `absolute paths of every flavour are refused`() {
        listOf(
            outside.resolve("passwd").toString(),
            "/etc/passwd",
            "C:/Windows/win.ini",
            "C:\\Windows\\win.ini",
            "C:win.ini",
            "\\\\server\\share\\file",
            "//server/share/file",
        ).forEach(::refused)
    }

    @Test
    fun `backslashes colons and stream names are refused`() {
        listOf("sub\\b.txt", "a.txt:stream", "a:b", "sub/b.txt::\$DATA").forEach(::refused)
    }

    @Test
    fun `empty parts blanks and control characters are refused`() {
        listOf("", "   ", "sub//b.txt", "sub/", "/", "a.txt\u0000", "a\n.txt", "a\t.txt").forEach(::refused)
    }

    @Test
    fun `names that the operating system would quietly change are refused`() {
        listOf("a.txt.", "a.txt ", " a.txt", "sub/b.txt.", "NUL", "con.txt", "sub/COM1", "LPT9.log").forEach(::refused)
    }

    @Test
    fun `a missing file and a directory are refused with a message that says why`() {
        val missing = assertFailsWith<IllegalArgumentException> { sandbox.existingFile("nope.txt") }
        val directory = assertFailsWith<IllegalArgumentException> { sandbox.existingFile("sub") }

        assertTrue(missing.message!!.contains("no file"), missing.message)
        assertTrue(directory.message!!.contains("not a file"), directory.message)
    }

    @Test
    fun `a path longer than the limit is refused`() {
        refused("a/".repeat(600) + "x")
    }

    @Test
    fun `a symbolic link that points outside is refused and one that points inside is followed`() {
        val escape = tryLink(inside.resolve("escape.txt"), outside.resolve("passwd"))
        val folder = tryLink(inside.resolve("outdir"), outside)
        val inner = tryLink(inside.resolve("inner.txt"), inside.resolve("a.txt"))
        assumeTrue(escape && folder && inner, "this system does not let the test create symbolic links")

        refused("escape.txt")
        refused("outdir/passwd")
        assertEquals(inside.toRealPath().resolve("a.txt"), sandbox.existingFile("inner.txt"))
    }

    @Test
    fun `a file larger than the upload cap is refused`() {
        val big = inside.resolve("big.bin")
        Files.newByteChannel(big, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE).use {
            it.position(FilesSandbox.MAX_UPLOAD_BYTES)
            it.write(java.nio.ByteBuffer.wrap(byteArrayOf(1)))
        }

        val problem = assertFailsWith<IllegalArgumentException> { sandbox.existingFile("big.bin") }

        assertTrue(problem.message!!.contains("larger"), problem.message)
    }

    // --- writing

    @Test
    fun `a new file lands directly inside the directory`() {
        assertEquals(inside.toRealPath().resolve("photo.jpg"), sandbox.newFile("photo.jpg"))
    }

    @Test
    fun `a new file never replaces an existing one`() {
        val problem = assertFailsWith<IllegalArgumentException> { sandbox.newFile("a.txt") }

        assertTrue(problem.message!!.contains("already exists"), problem.message)
        assertEquals("a", Files.readString(inside.resolve("a.txt")))
    }

    @Test
    fun `a new file name cannot hold a folder a traversal or an odd character`() {
        listOf(
            "sub/new.txt", "../new.txt", "..", ".", "", "a/b", "a\\b", "new.txt.", "new.txt ", "a:b", "a*b", "a?b",
            "a<b", "a|b", "nul", "AUX.txt", "x".repeat(256),
        ).forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { sandbox.newFile(name) }
        }
    }

    @Test
    fun `a new file name that is a link to somewhere else counts as existing`() {
        assumeTrue(tryLink(inside.resolve("dangling"), outside.resolve("missing")), "no symbolic links here")

        assertFailsWith<IllegalArgumentException> { sandbox.newFile("dangling") }
    }

    @Test
    fun `the sandbox needs a directory that exists`() {
        assertFailsWith<IllegalArgumentException> { FilesSandbox.on(inside.resolve("a.txt")) }
        assertFailsWith<IllegalArgumentException> { FilesSandbox.on(base.resolve("missing")) }
    }

    @Test
    fun `a name relative to the directory is reported with forward slashes`() {
        assertEquals("sub/b.txt", sandbox.relativeName(inside.resolve("sub").resolve("b.txt")))
    }

    private fun tryLink(link: Path, target: Path): Boolean =
        try {
            Files.createSymbolicLink(link, target)
            true
        } catch (_: Exception) {
            false
        }
}
