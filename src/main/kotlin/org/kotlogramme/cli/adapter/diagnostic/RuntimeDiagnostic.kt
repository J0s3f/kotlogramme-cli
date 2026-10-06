package org.kotlogramme.cli.adapter.diagnostic

import org.kotlogramme.cli.application.port.spi.Diagnostic
import org.kotlogramme.cli.domain.Finding

/**
 * Says which build is running, on what, and whether it is a JVM or a native image.
 *
 * Most of what `doctor` reports differs between the two, so this line is what makes a pasted report
 * readable.
 */
class RuntimeDiagnostic(
    private val version: String,
    private val property: (String) -> String? = System::getProperty,
) : Diagnostic {
    override val name = "Runtime"

    override fun run(): Finding {
        val java = "Java ${value("java.version")} (${value("java.vendor")})"
        val platform = "${value("os.name")} ${value("os.arch")}"
        return Finding.ok("kotlogramme $version, $java, $platform, $runtimeKind")
    }

    private val runtimeKind: String
        get() = if (property(NATIVE_IMAGE_PROPERTY) != null) "native image" else "JVM"

    private fun value(key: String): String = property(key) ?: "unknown"

    private companion object {
        /** Set by GraalVM in an image at run time, and absent on a JVM. */
        const val NATIVE_IMAGE_PROPERTY = "org.graalvm.nativeimage.imagecode"
    }
}
