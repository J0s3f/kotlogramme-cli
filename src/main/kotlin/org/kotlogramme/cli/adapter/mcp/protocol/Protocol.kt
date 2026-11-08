package org.kotlogramme.cli.adapter.mcp.protocol

/**
 * The protocol revisions this server speaks.
 *
 * [MODERN] is the stateless revision: no handshake, every request carries its own version and
 * capabilities. The [LEGACY] revisions open with `initialize`. Spec:
 * https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning
 */
object ProtocolVersions {
    const val MODERN = "2026-07-28"

    /** Handshake revisions, newest first. */
    val LEGACY = listOf("2025-11-25", "2025-06-18")

    val LATEST_LEGACY = LEGACY.first()

    /** Every revision this server answers to, newest first, as it advertises them. */
    val SUPPORTED = listOf(MODERN) + LEGACY
}

/**
 * The JSON-RPC and MCP error codes this server emits.
 *
 * `-32020..-32099` is reserved for the MCP specification; only codes the specification defines are used
 * from it. Spec: https://modelcontextprotocol.io/specification/2026-07-28/basic#error-codes
 */
object McpErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val UNSUPPORTED_PROTOCOL_VERSION = -32022
}

/** The `_meta` keys the stateless revision reserves for the protocol itself. */
object MetaKeys {
    const val PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion"
    const val CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
    const val SERVER_INFO = "io.modelcontextprotocol/serverInfo"
}
