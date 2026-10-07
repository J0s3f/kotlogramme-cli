package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.RawUpdate
import kotlinx.serialization.json.Json
import org.kotlogramme.raw.RawValue
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class RawUpdateJsonTest {
    private fun rawUpdate(hex: String) = RawUpdate("update", HexFormat.of().parseHex(hex))

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `decodes a read receipt into its named fields`() {
        val read = rawUpdate("99bc849e0000000022175159b50f59110000000043c1090000000000ec96120001000000")

        assertEquals(
            json(
                """{"peer":{"constructor":"peerUser","user_id":291049397},"max_id":639299,""" +
                    """"still_unread_count":0,"pts":1218284,"pts_count":1}""",
            ),
            json(read.toJson()),
        )
    }

    @Test
    fun `decodes a contact status change`() {
        val status = rawUpdate("def8bde5b50f5911000000004939b9edec07c56a")

        val decoded = json(status.toJson()).toString()

        assertEquals(true, decoded.contains("\"user_id\":291049397"), decoded)
    }

    @Test
    fun `an empty payload is kept as empty hex`() {
        assertEquals(json("""{"undecoded":""}"""), json(rawUpdate("").toJson()))
    }

    @Test
    fun `a truncated payload is kept as hex and not dropped`() {
        val truncated = rawUpdate("99bc849e00000000")

        assertEquals(json("""{"undecoded":"99bc849e00000000"}"""), json(truncated.toJson()))
    }

    @Test
    fun `decoding never throws for an unknown constructor`() {
        val unknown = rawUpdate("ffffffff00000000")

        assertEquals(json("""{"undecoded":"ffffffff00000000"}"""), json(unknown.toJson()))
    }

    @Test
    fun `keeps a payload the schema cannot decode as hex`() {
        val unknown = rawUpdate("01020304")

        assertEquals(json("""{"undecoded":"01020304"}"""), json(unknown.toJson()))
    }


    @Test
    fun `every kind of TL value becomes its JSON counterpart`() {
        assertEquals(json("true"), RawValue.BooleanValue(true).toJson())
        assertEquals(json("7"), RawValue.IntValue(7).toJson())
        assertEquals(json("9000000000"), RawValue.LongValue(9_000_000_000L).toJson())
        assertEquals(json("1.5"), RawValue.DoubleValue(1.5).toJson())
        assertEquals(json("\"text\""), RawValue.StringValue("text").toJson())
        assertEquals(json("\"0aff\""), RawValue.BytesValue(byteArrayOf(0x0a, 0xff.toByte())).toJson())
    }

    @Test
    fun `a vector becomes an array and an object keeps its constructor name`() {
        val vector = RawValue.VectorValue(listOf(RawValue.IntValue(1), RawValue.IntValue(2)))

        assertEquals(json("[1,2]"), vector.toJson())
    }
}
