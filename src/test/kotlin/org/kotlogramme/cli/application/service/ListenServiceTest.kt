package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.UpdateLoop
import org.kotlogramme.cli.domain.IncomingUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListenServiceTest {
    @Test
    fun `forwards each update and returns how many it handled`() {
        val loop = FakeUpdateLoop(listOf(IncomingUpdate.Other("typing"), IncomingUpdate.Other("read")))
        val received = mutableListOf<IncomingUpdate>()

        val handled = ListenService(loop).run(stop = { received.size == 2 }, onUpdate = { received += it })

        assertEquals(2, handled)
        assertEquals(listOf<IncomingUpdate>(IncomingUpdate.Other("typing"), IncomingUpdate.Other("read")), received)
    }

    @Test
    fun `stops after the update that asked it to`() {
        val loop = FakeUpdateLoop(listOf(IncomingUpdate.Other("first"), IncomingUpdate.Other("second")))
        val received = mutableListOf<IncomingUpdate>()

        val handled = ListenService(loop).run(stop = { received.isNotEmpty() }, onUpdate = { received += it })

        // The loop owns the thread, so the predicate is answered between updates: the second one is
        // already on its way when the first asks to stop, which is why a stop costs one short poll
        // and not the whole stream.
        assertEquals(2, handled)
        assertEquals(listOf<IncomingUpdate>(IncomingUpdate.Other("first"), IncomingUpdate.Other("second")), received)
        assertEquals(1, loop.stops)
    }

    @Test
    fun `a quiet loop is stopped promptly`() {
        // The source never delivers an update: without a prompt stop this would wait out the facade's
        // 30 s poll. The bound is generous next to the loop's 250 ms poll and still far below 30 s.
        val loop = FakeUpdateLoop(updates = emptyList(), quietMillis = 250L)

        val elapsed = measureMillis { ListenService(loop).run(stop = { true }, onUpdate = {}) }

        assertEquals(0, loop.handled)
        assertTrue(elapsed < 2_000, "stop took ${elapsed}ms, which is not prompt")
    }

    @Test
    fun `stops a loop that is not running without failing`() {
        val loop = FakeUpdateLoop(updates = emptyList(), start = false)

        val handled = ListenService(loop).run(stop = { true }, onUpdate = {})

        assertEquals(0, handled)
        assertEquals(1, loop.stops)
    }

    @Test
    fun `starting twice does not put two readers on the stream`() {
        val loop = FakeUpdateLoop(updates = listOf(IncomingUpdate.Other("only")))

        assertEquals(true, loop.start {})
        assertEquals(false, loop.start {})

        assertEquals(1, loop.readerCount)
    }
}

private inline fun measureMillis(body: () -> Unit): Long {
    val before = System.nanoTime()
    body()
    return (System.nanoTime() - before) / 1_000_000
}

/**
 * An [UpdateLoop] with no thread behind it.
 *
 * It replays its updates through the callback and counts as running until stopped, which is enough
 * for a service test to observe the drive-until-stop shape. [quietMillis] makes every callback look
 * like one poll of a quiet stream, so the prompt-stop bound is exercised without a real thread.
 */
internal class FakeUpdateLoop(
    private val updates: List<IncomingUpdate>,
    private val quietMillis: Long = 0L,
    private val start: Boolean = true,
) : UpdateLoop {
    var handled = 0
        private set
    var stops = 0
        private set
    var readerCount = 0
        private set

    private var running = false

    override fun start(onUpdate: (IncomingUpdate) -> Unit): Boolean {
        if (running) return false
        running = start
        readerCount++
        for (update in updates) {
            if (quietMillis > 0) Thread.sleep(quietMillis)
            onUpdate(update)
            handled++
        }
        return start
    }

    override fun stop() {
        stops++
        running = false
    }

    override val isRunning: Boolean get() = running
}
