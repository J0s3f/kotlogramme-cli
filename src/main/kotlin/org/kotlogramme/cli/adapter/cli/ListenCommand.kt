package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.kotlogramme.cli.adapter.format.renderUpdate
import org.kotlogramme.cli.adapter.format.renderUpdateJson
import org.kotlogramme.cli.domain.IncomingUpdate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Follows the live update stream until interrupted.
 *
 * The stream is followed on the facade's own background update loop, which polls in short waits and
 * joins its thread on stop, so a Ctrl-C shutdown hook only has to flip the stop flag: the loop
 * notices at its next short poll and the command returns normally instead of the process dying
 * mid-print. No worker thread of its own is started - the facade loop is the only reader. Updates
 * that carry no message, such as read receipts and contact status, are hidden unless `--all` is given.
 * `--once` stops after the first shown update, which is what makes the command scriptable; `--json`
 * prints one JSON object per line instead of the human rendering.
 */
class ListenCommand : CliktCommand(name = "listen") {
    private val appContext by requireObject<AppContext>()

    private val once by option("--once", help = "Stop after the first update").flag()
    private val all by option("--all", help = "Also show updates that carry no message, with their data as hex").flag()
    private val json by option("--json", help = "Print one JSON object per update").flag()

    override fun run() {
        val stop = AtomicBoolean(false)
        val shutdownHook = Thread { stop.set(true) }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            drive(stop)
        } finally {
            removeShutdownHook(shutdownHook)
        }
    }

    private fun drive(stop: AtomicBoolean) {
        val listen = appContext.listen()
        val onUpdate: (IncomingUpdate) -> Unit = { update ->
            if (isShown(update)) {
                printUpdate(update)
                if (once) stop.set(true)
            }
        }
        listen.run(stop = { stop.get() }, onUpdate = onUpdate)
    }

    private fun isShown(update: IncomingUpdate): Boolean = all || update is IncomingUpdate.NewMessage

    private fun printUpdate(update: IncomingUpdate) {
        if (json) {
            appContext.output.renderUpdateJson(update)
        } else {
            appContext.output.renderUpdate(update, appContext.messageStyler)
        }
    }

    private fun removeShutdownHook(hook: Thread) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (error: IllegalStateException) {
            // The JVM is already shutting down; the hook has run or is about to.
        }
    }
}
