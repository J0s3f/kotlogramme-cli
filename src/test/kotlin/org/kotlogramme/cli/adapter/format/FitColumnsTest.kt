package org.kotlogramme.cli.adapter.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A table spends `3 * columns + 1` columns on borders and padding, so a budget is `available - that`. */
class FitColumnsTest {
    @Test
    fun `keeps the natural widths when they fit`() {
        assertEquals(listOf(5, 10), fitColumns(natural = listOf(5, 10), floors = listOf(5, 6), available = 30))
    }

    @Test
    fun `keeps the natural widths when they fit exactly`() {
        assertEquals(listOf(5, 10), fitColumns(natural = listOf(5, 10), floors = listOf(5, 6), available = 22))
    }

    @Test
    fun `narrows the widest column first`() {
        assertEquals(listOf(5, 18), fitColumns(natural = listOf(5, 40), floors = listOf(5, 6), available = 30))
    }

    @Test
    fun `narrows equally wide columns evenly`() {
        assertEquals(listOf(17, 17), fitColumns(natural = listOf(30, 30), floors = listOf(6, 6), available = 41))
    }

    @Test
    fun `hands the spare columns out left to right so the whole width is used`() {
        val widths = fitColumns(natural = listOf(30, 30, 30), floors = listOf(6, 6, 6), available = 47)

        assertEquals(listOf(13, 12, 12), widths)
    }

    @Test
    fun `leaves narrow columns alone`() {
        assertEquals(listOf(3, 23, 4), fitColumns(natural = listOf(3, 50, 4), floors = listOf(3, 6, 4), available = 40))
    }

    @Test
    fun `never narrows a column below its floor`() {
        assertEquals(listOf(15, 8), fitColumns(natural = listOf(20, 20), floors = listOf(15, 6), available = 30))
    }

    @Test
    fun `drops the floors to the minimum when the floors alone do not fit`() {
        assertEquals(listOf(12, 11), fitColumns(natural = listOf(20, 20), floors = listOf(20, 20), available = 30))
    }

    @Test
    fun `overflows at the minimum width when nothing fits`() {
        assertEquals(listOf(6, 6), fitColumns(natural = listOf(20, 20), floors = listOf(6, 6), available = 10))
    }

    @Test
    fun `a column narrower than the minimum keeps its own width when the table is squeezed`() {
        assertEquals(listOf(2, 6), fitColumns(natural = listOf(2, 40), floors = listOf(2, 6), available = 5))
    }

    @Test
    fun `a single column takes the whole width`() {
        assertEquals(listOf(26), fitColumns(natural = listOf(50), floors = listOf(6), available = 30))
    }

    @Test
    fun `no columns give no widths`() {
        assertEquals(emptyList(), fitColumns(natural = emptyList(), floors = emptyList(), available = 80))
    }

    @Test
    fun `a width of zero or less overflows instead of throwing`() {
        assertEquals(listOf(6, 6), fitColumns(natural = listOf(20, 20), floors = listOf(6, 6), available = 0))
        assertEquals(listOf(6, 6), fitColumns(natural = listOf(20, 20), floors = listOf(6, 6), available = -5))
    }

    @Test
    fun `the result never exceeds the budget while it can fit`() {
        val natural = listOf(4, 12, 80, 33, 7)
        val floors = natural.map { minOf(it, 6) }
        val smallestTable = floors.sum() + 3 * natural.size + 1

        for (available in smallestTable..200) {
            val widths = fitColumns(natural, floors, available)

            assertTrue(widths.sum() + 3 * natural.size + 1 <= available, "$available -> $widths")
        }
    }

    @Test
    fun `no column is ever wider than its natural width`() {
        val natural = listOf(4, 12, 80, 33, 7)
        val floors = natural.map { minOf(it, 6) }

        for (available in 20..300) {
            val widths = fitColumns(natural, floors, available)

            assertTrue(widths.indices.all { widths[it] <= natural[it] }, "$available -> $widths")
        }
    }

    @Test
    fun `no column is ever narrower than its floor or the minimum`() {
        val natural = listOf(10, 30, 50)
        val floors = listOf(10, 6, 6)

        for (available in 10..200) {
            val widths = fitColumns(natural, floors, available)

            assertTrue(widths.indices.all { widths[it] >= minOf(natural[it], MIN_COLUMN_WIDTH) }, "$available -> $widths")
        }
    }

    @Test
    fun `a wider terminal never makes a column narrower`() {
        val natural = listOf(9, 40, 25)
        val floors = listOf(6, 6, 6)
        var previous = fitColumns(natural, floors, 30)

        for (available in 31..150) {
            val widths = fitColumns(natural, floors, available)

            assertTrue(widths.indices.all { widths[it] >= previous[it] }, "$available: $previous -> $widths")
            previous = widths
        }
    }
}
