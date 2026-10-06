package org.kotlogramme.cli.adapter.system

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse

class SystemClockTest {
    @Test
    fun `reports the current time`() {
        val before = Instant.now()

        val reading = SystemClock().now()

        val after = Instant.now()
        assertFalse(reading.isBefore(before) || reading.isAfter(after), "$reading is not between $before and $after")
    }

    @Test
    fun `moves forward between readings`() {
        val clock = SystemClock()

        val first = clock.now()
        Thread.sleep(2)

        assertFalse(clock.now().isBefore(first))
    }
}
