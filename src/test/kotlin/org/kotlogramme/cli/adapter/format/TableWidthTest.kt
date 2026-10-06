package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableWidthTest {
    @Test
    fun `no option detects the terminal`() {
        assertEquals(TableWidth.Detect, TableWidth.of(null))
    }

    @Test
    fun `zero means never wrap`() {
        assertEquals(TableWidth.Unlimited, TableWidth.of(0))
    }

    @Test
    fun `any other number is a fixed width`() {
        assertEquals(TableWidth.Fixed(80), TableWidth.of(80))
        assertEquals(TableWidth.Fixed(1), TableWidth.of(1))
    }

    @Test
    fun `detect uses the width of the terminal`() {
        assertEquals(120, TableWidth.Detect.resolve { 120 })
    }

    @Test
    fun `detect has no width off a terminal`() {
        assertNull(TableWidth.Detect.resolve { null })
    }

    @Test
    fun `unlimited ignores the terminal`() {
        assertNull(TableWidth.Unlimited.resolve { 120 })
    }

    @Test
    fun `a fixed width ignores the terminal`() {
        assertEquals(60, TableWidth.Fixed(60).resolve { 120 })
    }

    @Test
    fun `a fixed width applies off a terminal too`() {
        assertEquals(60, TableWidth.Fixed(60).resolve { null })
    }

    @Test
    fun `a fixed or unlimited width never asks the terminal`() {
        TableWidth.Fixed(60).resolve { error("asked the terminal") }
        TableWidth.Unlimited.resolve { error("asked the terminal") }
    }
}
