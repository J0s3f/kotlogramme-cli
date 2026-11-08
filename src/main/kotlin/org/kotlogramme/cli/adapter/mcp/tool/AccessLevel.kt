package org.kotlogramme.cli.adapter.mcp.tool

/**
 * How much of the account a server instance may touch, from least to most.
 *
 * An MCP client lets a model call tools on the user's own Telegram account, so the default is the least:
 * [READ_ONLY]. Tools above the chosen level are not listed and are refused if called by name.
 */
enum class AccessLevel(val cliName: String) {
    READ_ONLY("read-only"),
    READ_WRITE("read-write"),
    FULL("full"),
    ;

    /** Whether a server run at this level may offer a tool that [required] this level. */
    fun permits(required: AccessLevel): Boolean = required.ordinal <= ordinal

    companion object {
        val DEFAULT = READ_ONLY

        fun fromCliName(name: String): AccessLevel =
            entries.firstOrNull { it.cliName == name }
                ?: throw IllegalArgumentException("Unknown access level '$name'; use ${entries.joinToString { it.cliName }}")
    }
}
