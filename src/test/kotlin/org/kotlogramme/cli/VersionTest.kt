package org.kotlogramme.cli

import kotlin.test.Test
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun `the reported version looks like a semantic version`() {
        assertTrue(Regex("""\d+\.\d+\.\d+.*""").matches(VERSION))
    }
}
