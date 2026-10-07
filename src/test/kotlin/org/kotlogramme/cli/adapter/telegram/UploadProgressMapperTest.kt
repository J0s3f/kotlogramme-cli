package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.UploadProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import com.github.badoualy.telegram.api.UploadProgress as FacadeProgress

class UploadProgressMapperTest {
    @Test
    fun `the facade's counts and time carry over and its own rate is not used`() {
        val facade = FacadeProgress(100, 400, 99_999.0, 2000)

        val progress = facade.toUploadProgress()

        assertEquals(UploadProgress(bytesSent = 100, totalBytes = 400, elapsedMillis = 2000), progress)
        assertEquals(50.0, progress.bytesPerSecond)
    }
}
