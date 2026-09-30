package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.UpdateSource
import org.kotlogramme.cli.domain.IncomingUpdate
import kotlin.test.Test
import kotlin.test.assertEquals

class ListenServiceTest {
    @Test
    fun `forwards each update and returns how many it handled`() {
        val source = FakeUpdateSource(listOf(IncomingUpdate.Other("typing"), IncomingUpdate.Other("read")))
        val received = mutableListOf<IncomingUpdate>()

        val handled = ListenService(source).run(stop = { received.size == 2 }, onUpdate = { received += it })

        assertEquals(2, handled)
        assertEquals(listOf<IncomingUpdate>(IncomingUpdate.Other("typing"), IncomingUpdate.Other("read")), received)
    }

    @Test
    fun `stops as soon as the predicate asks`() {
        val source = FakeUpdateSource(
            listOf(IncomingUpdate.Other("first"), IncomingUpdate.Other("second")),
        )
        val received = mutableListOf<IncomingUpdate>()

        val handled = ListenService(source).run(stop = { received.isNotEmpty() }, onUpdate = { received += it })

        assertEquals(1, handled)
        assertEquals(listOf<IncomingUpdate>(IncomingUpdate.Other("first")), received)
        assertEquals(1, source.timeouts.size)
    }

    @Test
    fun `a timeout does not end the loop`() {
        val update = IncomingUpdate.Other("later")
        val source = FakeUpdateSource(listOf(null, update))
        val received = mutableListOf<IncomingUpdate>()

        val handled = ListenService(source).run(stop = { received.isNotEmpty() }, onUpdate = { received += it })

        assertEquals(1, handled)
        assertEquals(listOf<IncomingUpdate>(update), received)
        assertEquals(2, source.timeouts.size)
    }

    @Test
    fun `polls the source with the facade default timeout`() {
        val source = FakeUpdateSource(listOf(null))

        ListenService(source).run(stop = { source.timeouts.isNotEmpty() }, onUpdate = {})

        assertEquals(listOf(ListenService.DEFAULT_TIMEOUT_MILLIS), source.timeouts)
    }
}

internal class FakeUpdateSource(private val updates: List<IncomingUpdate?>) : UpdateSource {
    val timeouts = mutableListOf<Long>()
    private var index = 0

    override fun next(timeoutMillis: Long): IncomingUpdate? {
        timeouts += timeoutMillis
        return updates.getOrNull(index++)
    }
}
