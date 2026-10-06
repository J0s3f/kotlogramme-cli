package org.kotlogramme.cli.adapter.system

import org.kotlogramme.cli.application.port.spi.Clock
import java.time.Instant

/** The [Clock] backed by the machine's system time. */
class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
