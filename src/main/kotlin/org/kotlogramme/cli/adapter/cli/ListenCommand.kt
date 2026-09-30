package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.kotlogramme.cli.adapter.format.renderUpdate
import org.kotlogramme.cli.adapter.format.renderUpdateJson
import org.kotlogramme.cli.domain.IncomingUpdate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Follows the live update stream until interrupted.
 *
 * The blocking listen loop runs on a worker thread while a Ctrl-C shutdown hook flips the stop flag,
 * so the loop ends at its next check and the command returns normally instead of the process dying
 * mid-print. `--once` stops after the first update, which is what makes the command scriptable;
 * `--json` prints one JSON object per line instead of the human rendering.
 */
class ListenCommand : CliktCommand(name = "listen") {
    private val appContext by requireObject<AppContext>()

    private val once by option("--once", help = "Stop after the first update").flag()
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
        val failure = AtomicReference<Throwable?>()
        val onUpdate: (IncomingUpdate) -> Unit = { update ->
            printUpdate(update)
            if (once) stop.set(true)
        }
        val worker = Thread {
            try {
                listen.run(stop = { stop.get() }, onUpdate = onUpdate)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join()
        failure.get()?.let { throw it }
    }

    private fun printUpdate(update: IncomingUpdate) {
        if (json) appContext.output.renderUpdateJson(update) else appContext.output.renderUpdate(update)
    }

    private fun removeShutdownHook(hook: Thread) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (error: IllegalStateException) {
            // The JVM is already shutting down; the hook has run or is about to.
        }
    }
}
