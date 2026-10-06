package org.kotlogramme.cli.application.port.spi

import java.time.Instant

/**
 * The current time, as an outbound port.
 *
 * Anything that expires or compares against "now" asks this instead of reading the system clock, so
 * a test can move time by hand instead of waiting for it.
 */
interface Clock {
    fun now(): Instant
}
