package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * One argument of a tool.
 *
 * The same object describes the argument to the model (its JSON Schema) and reads it back, so a tool's
 * schema can never say something its code does not enforce. A parameter that fails to parse throws
 * [IllegalArgumentException] with a sentence the model can act on; the server reports it as a tool error.
 */
sealed class Parameter(val name: String, val description: String) {
    abstract val required: Boolean

    abstract fun schema(): JsonObject

    /** The value to use when the argument is absent, or `null` when there is none. */
    abstract val default: Any?

    abstract fun parse(raw: JsonElement): Any

    protected fun described(type: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject {
            put("type", type)
            put("description", description)
            extra()
        }

    protected fun mismatch(expected: String): Nothing =
        throw IllegalArgumentException("The argument '$name' must be $expected.")
}

class StringParameter(
    name: String,
    description: String,
    override val required: Boolean = false,
) : Parameter(name, description) {
    override val default: Any? = null

    override fun schema() = described("string") { put("maxLength", MAX_LENGTH) }

    override fun parse(raw: JsonElement): Any {
        val primitive = raw as? JsonPrimitive
        if (primitive == null || raw is JsonNull || !primitive.isString) mismatch("a string")
        val text = primitive.content
        require(text.length <= MAX_LENGTH) { "The argument '$name' is longer than $MAX_LENGTH characters." }
        require(!required || text.isNotBlank()) { "The argument '$name' must not be blank." }
        return text
    }

    companion object {
        /** Telegram's own message limit is 4096; no argument of ours needs more. */
        const val MAX_LENGTH = 4096
    }
}

class IntParameter(
    name: String,
    description: String,
    private val min: Int,
    private val max: Int,
    override val default: Int? = null,
    override val required: Boolean = false,
) : Parameter(name, description) {
    override fun schema() = described("integer") {
        put("minimum", min)
        put("maximum", max)
        default?.let { put("default", it) }
    }

    override fun parse(raw: JsonElement): Any {
        val primitive = raw as? JsonPrimitive
        if (primitive == null || raw is JsonNull || primitive.isString) mismatch("an integer")
        val value = primitive.intOrNull ?: mismatch("an integer")
        require(value in min..max) { "The argument '$name' must be between $min and $max." }
        return value
    }
}

class BooleanParameter(
    name: String,
    description: String,
    override val default: Boolean? = null,
    override val required: Boolean = false,
) : Parameter(name, description) {
    override fun schema() = described("boolean") { default?.let { put("default", it) } }

    override fun parse(raw: JsonElement): Any {
        val primitive = raw as? JsonPrimitive
        if (primitive == null || raw is JsonNull || primitive.isString) mismatch("a boolean")
        return primitive.booleanOrNull ?: mismatch("a boolean")
    }
}

/** The arguments of one tool: their JSON Schema and the parser that enforces it. */
class ToolParameters(vararg parameters: Parameter) {
    private val declared: List<Parameter> = parameters.toList()

    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        if (declared.isNotEmpty()) {
            put("properties", JsonObject(declared.associate { it.name to it.schema() }))
            val required = declared.filter { it.required }
            if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it.name) }))
        }
        put("additionalProperties", false)
    }

    fun parse(arguments: JsonObject): ToolArguments {
        val unknown = arguments.keys - declared.map { it.name }.toSet()
        require(unknown.isEmpty()) {
            "Unknown argument '${unknown.first()}'. Valid arguments: " +
                (declared.joinToString { it.name }.ifEmpty { "none" }) + "."
        }
        val values = declared.associate { parameter ->
            val raw = arguments[parameter.name]
            val value = when {
                raw != null && raw !is JsonNull -> parameter.parse(raw)
                parameter.required -> throw IllegalArgumentException("The argument '${parameter.name}' is required.")
                else -> parameter.default
            }
            parameter.name to value
        }
        return ToolArguments(values)
    }
}

/** The parsed arguments of one call. Asking for an argument the tool never declared is a bug, not input. */
class ToolArguments(private val values: Map<String, Any?>) {
    fun string(name: String): String = stringOrNull(name) ?: error("'$name' was not given")

    fun stringOrNull(name: String): String? = value(name) as String?

    fun int(name: String): Int = value(name) as Int? ?: error("'$name' was not given")

    fun boolean(name: String): Boolean = value(name) as Boolean? ?: error("'$name' was not given")

    private fun value(name: String): Any? {
        check(values.containsKey(name)) { "The tool never declared an argument '$name'" }
        return values[name]
    }
}
