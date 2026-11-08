package org.kotlogramme.cli.adapter.mcp.tool

/** The call could not be done for a reason the model should read, for example that nobody is signed in. */
class ToolFailure(message: String) : RuntimeException(message)
