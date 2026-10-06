package org.kotlogramme.cli

import org.kotlogramme.cli.application.port.spi.Clock
import java.time.Duration
import java.time.Instant

/** A [Clock] a test moves by hand. */
internal class FakeClock(private var current: Instant = Instant.parse("2026-01-01T00:00:00Z")) : Clock {
    override fun now(): Instant = current

    fun advance(by: Duration) {
        current = current.plus(by)
    }
}
