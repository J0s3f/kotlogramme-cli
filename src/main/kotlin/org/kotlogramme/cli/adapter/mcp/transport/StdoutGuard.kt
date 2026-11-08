package org.kotlogramme.cli.adapter.mcp.transport

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Keeps stdout for the protocol alone.
 *
 * On stdio, a single stray byte on stdout corrupts the message stream. [claim] hands the process's real
 * stdout to the transport and points `System.out` at stderr, so a `println` from this code, the Telegram
 * facade or any library lands in the log instead of in the middle of a message.
 */
object StdoutGuard {
    fun claim(): OutputStream {
        val protocolOutput = FileOutputStream(FileDescriptor.out)
        System.setOut(System.err)
        return protocolOutput
    }
}
