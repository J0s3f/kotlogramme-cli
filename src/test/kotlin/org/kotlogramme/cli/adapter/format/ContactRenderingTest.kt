package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.Contact
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactRenderingTest {
    private val contacts = listOf(
        Contact(id = 1, displayName = "Ada", username = "ada", phoneNumber = "+15550100"),
        Contact(id = 2, displayName = "Bob", username = null, phoneNumber = "+15550101"),
    )

    private fun render(format: OutputFormat, block: ConsoleOutput.() -> Unit): String {
        val buffer = ByteArrayOutputStream()
        ConsoleOutput(format, PrintStream(buffer, true, Charsets.UTF_8)).block()
        return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").trimEnd()
    }

    @Test
    fun `a table shows the username and phone columns`() {
        val rendered = render(OutputFormat.TABLE) { renderContacts(contacts) }

        val expected = listOf(
            "+----+------+----------+-----------+",
            "| id | name | username | phone     |",
            "+----+------+----------+-----------+",
            "| 1  | Ada  | ada      | +15550100 |",
            "| 2  | Bob  |          | +15550101 |",
            "+----+------+----------+-----------+",
        ).joinToString("\n")
        assertEquals(expected, rendered)
    }

    @Test
    fun `plain contacts are tab separated`() {
        val rendered = render(OutputFormat.PLAIN) { renderContacts(contacts) }

        assertEquals(
            listOf(
                "id\tname\tusername\tphone",
                "1\tAda\tada\t+15550100",
                "2\tBob\t\t+15550101",
            ),
            rendered.lines(),
        )
    }

    @Test
    fun `json contacts are an array of objects`() {
        val rendered = render(OutputFormat.JSON) { renderContacts(contacts) }

        assertEquals(
            """[{"id":"1","name":"Ada","username":"ada","phone":"+15550100"},""" +
                """{"id":"2","name":"Bob","username":"","phone":"+15550101"}]""",
            rendered,
        )
    }
}
