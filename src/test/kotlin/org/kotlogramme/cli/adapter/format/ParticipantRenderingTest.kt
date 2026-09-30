package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Participant
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantRenderingTest {
    private val participants = listOf(
        Participant(id = 1, displayName = "Ada", username = "ada", role = "creator"),
        Participant(id = 2, displayName = "Bob", username = null, role = "member"),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the role column`() {
        val rendered = render(OutputFormat.TABLE) { renderParticipants(participants) }

        val expected = listOf(
            "+----+------+----------+---------+",
            "| id | name | username | role    |",
            "+----+------+----------+---------+",
            "| 1  | Ada  | ada      | creator |",
            "| 2  | Bob  |          | member  |",
            "+----+------+----------+---------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain participants are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderParticipants(participants) }

        assertEquals(
            listOf(
                "id\tname\tusername\trole",
                "1\tAda\tada\tcreator",
                "2\tBob\t\tmember",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json participants are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderParticipants(participants) }

        assertEquals(
            """[{"id":"1","name":"Ada","username":"ada","role":"creator"},""" +
                """{"id":"2","name":"Bob","username":"","role":"member"}]""",
            rendered,
        )
    }
}
