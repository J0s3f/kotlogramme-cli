package org.kotlogramme.cli.adapter.diagnostic

import kotlinx.serialization.json.JsonObject
import org.kotlogramme.cli.adapter.telegram.decodeUpdate
import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding
import org.kotlogramme.raw.RawTelegramApi
import java.util.HexFormat

/**
 * Loads the bundled Telegram schema and decodes a known update with it.
 *
 * This is what `listen --all` relies on, and it exercises the resource lookup and the serialization
 * that a native image has to be told about, so a missing resource shows up here instead of on the
 * first live update. It needs no network and no credentials.
 */
class TelegramSchemaDiagnostic(
    private val sample: ByteArray = SAMPLE_USER_STATUS,
) : Diagnostic {
    override val name = "Telegram schema"

    override fun run(): Finding {
        val schema = RawTelegramApi.schema()
        val decoded = runCatching { decodeUpdate(sample) }.getOrElse { error ->
            return Finding.failed("schema loaded but could not decode a sample update: ${error.message ?: error}")
        }
        check(decoded is JsonObject) { "the sample decoded to ${decoded::class.simpleName}, not an object" }
        return Finding.ok(
            "layer ${schema.layer}, ${schema.constructors.size} constructors, ${schema.functions.size} methods; " +
                "decoded a sample update",
        )
    }

    private companion object {
        /** `updateUserStatus` for user 291049397 going online, as Telegram serializes it. */
        val SAMPLE_USER_STATUS: ByteArray =
            HexFormat.of().parseHex("def8bde5b50f5911000000004939b9edec07c56a")
    }
}
