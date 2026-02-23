package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

object MeasurementRunViewParityContract {
    fun assertLegacyProgressionAndReadModel() {
        val controller = MeasurementRunViewController()
        val groupId = "parity-g-1"

        controller.setInVehicle(true)
        controller.onSequenceStarted(groupId)
        controller.onLocateStarted()
        controller.onLatencyStarted()
        controller.onLatencyMeasured(latencyMeasurement(groupId))
        controller.onDownloadStarted()
        controller.onThroughputMeasured(downloadMeasurement(groupId))
        controller.onUploadStarted()
        controller.onThroughputMeasured(uploadMeasurement(groupId))
        val completed = controller.onCompleted(
            group = controller.currentState().results,
            errorCode = null,
            errorText = null,
        )

        assertEquals(MeasurementRunProgress.END, completed.progress)
        assertTrue(completed.inVehicle)
        assertEquals(groupId, completed.results?.id)
        assertNull(completed.errorMessage)

        val model = MeasurementResultReadModelUseCase().present(completed)
        assertEquals("42 ms", model.latencyText)
        assertEquals("100 Mbps", model.downloadText)
        assertEquals("80 Mbps", model.uploadText)
        assertEquals("Pending sync", model.uploadedText)
        assertEquals("Measurement complete. Results saved and sync attempted.", model.summaryText)
    }

    private fun latencyMeasurement(groupId: String): Measurement {
        return Measurement(
            groupId = groupId,
            type = "latency",
            latencyData = LatencyData(rtt = 42_000),
        )
    }

    private fun downloadMeasurement(groupId: String): Measurement {
        return Measurement(
            groupId = groupId,
            type = "download",
            uploadDownloadData = UploadDownloadData(bytesPerSec = 12_500_000.0),
        )
    }

    private fun uploadMeasurement(groupId: String): Measurement {
        return Measurement(
            groupId = groupId,
            type = "upload",
            uploadDownloadData = UploadDownloadData(bytesPerSec = 10_000_000.0),
        )
    }
}
