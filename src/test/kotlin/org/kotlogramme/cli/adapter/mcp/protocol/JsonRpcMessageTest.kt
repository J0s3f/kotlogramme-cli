package org.kotlogramme.cli.adapter.mcp.protocol

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonRpcMessageTest {
    @Test
    fun `a request with an integer id`() {
        val message = JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":7,"method":"tools/list","params":{"a":1}}""")

        val request = assertIs<JsonRpcMessage.Request>(message)
        assertEquals(JsonPrimitive(7), request.id)
        assertEquals("tools/list", request.method)
        assertEquals(1, request.params?.size)
    }

    @Test
    fun `a request with a string id`() {
        val message = JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":"abc","method":"ping"}""")

        val request = assertIs<JsonRpcMessage.Request>(message)
        assertEquals(JsonPrimitive("abc"), request.id)
        assertNull(request.params)
    }

    @Test
    fun `a message without an id is a notification`() {
        val message = JsonRpcMessage.parse("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")

        assertEquals(JsonRpcMessage.Notification("notifications/initialized", null), message)
    }

    @Test
    fun `text that is not JSON is a parse error without an id`() {
        val invalid = assertIs<JsonRpcMessage.Invalid>(JsonRpcMessage.parse("{not json"))

        assertEquals(McpErrorCodes.PARSE_ERROR, invalid.error.code)
        assertNull(invalid.id)
    }

    @Test
    fun `a batch is an invalid request`() {
        val invalid = assertIs<JsonRpcMessage.Invalid>(JsonRpcMessage.parse("""[{"jsonrpc":"2.0","method":"x"}]"""))

        assertEquals(McpErrorCodes.INVALID_REQUEST, invalid.error.code)
    }

    @Test
    fun `a wrong protocol marker is an invalid request that keeps its id`() {
        val invalid = assertIs<JsonRpcMessage.Invalid>(JsonRpcMessage.parse("""{"jsonrpc":"1.0","id":3,"method":"x"}"""))

        assertEquals(McpErrorCodes.INVALID_REQUEST, invalid.error.code)
        assertEquals(JsonPrimitive(3), invalid.id)
    }

    @Test
    fun `a null id is invalid because the spec forbids it`() {
        val invalid = assertIs<JsonRpcMessage.Invalid>(JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":null,"method":"x"}"""))

        assertEquals(McpErrorCodes.INVALID_REQUEST, invalid.error.code)
        assertNull(invalid.id)
    }

    @Test
    fun `an id that is neither a string nor an integer is invalid`() {
        for (id in listOf("1.5", "true", "[1]", "{}")) {
            val invalid = assertIs<JsonRpcMessage.Invalid>(
                JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":$id,"method":"x"}"""),
                "id $id",
            )

            assertEquals(McpErrorCodes.INVALID_REQUEST, invalid.error.code)
        }
    }

    @Test
    fun `a response from the client is an invalid request`() {
        val invalid = assertIs<JsonRpcMessage.Invalid>(JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":1,"result":{}}"""))

        assertEquals(McpErrorCodes.INVALID_REQUEST, invalid.error.code)
    }

    @Test
    fun `params that are not an object are invalid params for that request`() {
        val invalid = assertIs<JsonRpcMessage.Invalid>(
            JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":4,"method":"tools/list","params":[1]}"""),
        )

        assertEquals(McpErrorCodes.INVALID_PARAMS, invalid.error.code)
        assertEquals(JsonPrimitive(4), invalid.id)
    }

    @Test
    fun `null params count as absent`() {
        val request = assertIs<JsonRpcMessage.Request>(
            JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":4,"method":"ping","params":null}"""),
        )

        assertNull(request.params)
        assertEquals(JsonNull, JsonNull)
    }

    @Test
    fun `non ASCII text survives`() {
        val request = assertIs<JsonRpcMessage.Request>(
            JsonRpcMessage.parse("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ä😀"}}"""),
        )

        assertEquals(JsonPrimitive("ä😀"), request.params?.get("name"))
    }
}
