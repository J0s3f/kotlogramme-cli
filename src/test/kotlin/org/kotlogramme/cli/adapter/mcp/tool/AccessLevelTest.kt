package org.kotlogramme.cli.adapter.mcp.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessLevelTest {
    @Test
    fun `each level permits itself and the ones below it`() {
        assertTrue(AccessLevel.READ_ONLY.permits(AccessLevel.READ_ONLY))
        assertFalse(AccessLevel.READ_ONLY.permits(AccessLevel.READ_WRITE))
        assertFalse(AccessLevel.READ_ONLY.permits(AccessLevel.FULL))
        assertTrue(AccessLevel.READ_WRITE.permits(AccessLevel.READ_ONLY))
        assertTrue(AccessLevel.READ_WRITE.permits(AccessLevel.READ_WRITE))
        assertFalse(AccessLevel.READ_WRITE.permits(AccessLevel.FULL))
        assertTrue(AccessLevel.FULL.permits(AccessLevel.FULL))
    }

    @Test
    fun `the default is the safest level`() {
        assertEquals(AccessLevel.READ_ONLY, AccessLevel.DEFAULT)
    }

    @Test
    fun `levels are named on the command line`() {
        assertEquals(AccessLevel.READ_WRITE, AccessLevel.fromCliName("read-write"))
        assertFailsWith<IllegalArgumentException> { AccessLevel.fromCliName("root") }
    }
}
