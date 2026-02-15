package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementRunViewControllerTest {
    @Test
    fun controller_followsLegacyProgressOrder_andAggregatesMeasurements() {
        val controller = MeasurementRunViewController()
        val groupId = "g-123"

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
        assertEquals("latency", completed.results?.latency?.type)
        assertEquals("download", completed.results?.download?.type)
        assertEquals("upload", completed.results?.upload?.type)
        assertNull(completed.errorMessage)
        assertEquals(MeasurementRunErrorCategory.NONE, completed.errorCategory)
    }

    @Test
    fun controller_failedCompletion_usesRateLimitMessageFor429() {
        val controller = MeasurementRunViewController()

        val failed = controller.onCompleted(
            group = null,
            errorCode = 429,
            errorText = "raw backend text",
        )

        assertEquals(MeasurementRunProgress.ERROR, failed.progress)
        assertEquals(MeasurementRunViewController.RATE_LIMITED_ERROR_MESSAGE, failed.errorMessage)
        assertEquals(MeasurementRunErrorCategory.SERVER, failed.errorCategory)
    }

    @Test
    fun controller_failedCompletion_usesFallbackMessageWhenErrorTextMissing() {
        val controller = MeasurementRunViewController()

        val failed = controller.onCompleted(
            group = null,
            errorCode = 500,
            errorText = null,
        )

        assertEquals(MeasurementRunProgress.ERROR, failed.progress)
        assertEquals(MeasurementRunViewController.DEFAULT_ERROR_MESSAGE, failed.errorMessage)
        assertEquals(MeasurementRunErrorCategory.UNKNOWN, failed.errorCategory)
    }

    @Test
    fun controller_failedCompletion_classifiesDeterministicErrorCategories() {
        val controller = MeasurementRunViewController()

        val network = controller.onCompleted(
            group = null,
            errorCode = null,
            errorText = "Network timeout while uploading",
        )
        assertEquals(MeasurementRunErrorCategory.NETWORK, network.errorCategory)

        val auth = controller.onCompleted(
            group = null,
            errorCode = 401,
            errorText = "Unauthorized: invalid api key",
        )
        assertEquals(MeasurementRunErrorCategory.AUTH_CONFIG, auth.errorCategory)

        val server = controller.onCompleted(
            group = null,
            errorCode = 500,
            errorText = "Server protocol decode failure",
        )
        assertEquals(MeasurementRunErrorCategory.SERVER, server.errorCategory)
    }

    @Test
    fun presenter_mapsLegacyHeaders_andTerminalVisibility() {
        val presenter = MeasurementRunUiPresenter()

        val locate = presenter.present(MeasurementRunState(progress = MeasurementRunProgress.LOCATE))
        assertEquals(MeasurementRunHeaderKey.FINDING_SERVER, locate.headerKey)
        assertEquals("Finding server", locate.headerText)
        assertTrue(locate.showProgressBar)
        assertFalse(locate.showCompletionActions)

        val completed = presenter.present(MeasurementRunState(progress = MeasurementRunProgress.END))
        assertEquals(MeasurementRunHeaderKey.MEASUREMENT_COMPLETE, completed.headerKey)
        assertFalse(completed.showProgressBar)
        assertTrue(completed.showCompletionActions)

        val failed = presenter.present(
            MeasurementRunState(
                progress = MeasurementRunProgress.ERROR,
                errorMessage = "Network timeout",
            ),
        )
        assertEquals("Measurement failed: Network timeout", failed.headerText)
        assertFalse(failed.showProgressBar)
        assertTrue(failed.showCompletionActions)
    }

    @Test
    fun resultReadModel_formatsCompletedMetricsForUserView() {
        val useCase = MeasurementResultReadModelUseCase()
        val state = MeasurementRunState(
            progress = MeasurementRunProgress.END,
            results = MeasurementGroup(
                latency = latencyMeasurement("g-1").copy(latencyData = LatencyData(rtt = 45_000)),
                download = downloadMeasurement("g-1").copy(
                    uploadDownloadData = UploadDownloadData(bytesPerSec = 12_500_000.0),
                ),
                upload = uploadMeasurement("g-1").copy(
                    uploadDownloadData = UploadDownloadData(bytesPerSec = 3_750_000.0),
                ),
                submission = null,
            ),
        )

        val model = useCase.present(state)
        assertEquals("45 ms", model.latencyText)
        assertEquals("100 Mbps", model.downloadText)
        assertEquals("30 Mbps", model.uploadText)
        assertEquals("Measurement complete. Results saved and sync attempted.", model.summaryText)
    }

    @Test
    fun resultReadModel_usesErrorSummaryForFailedState() {
        val useCase = MeasurementResultReadModelUseCase()
        val model = useCase.present(
            MeasurementRunState(
                progress = MeasurementRunProgress.ERROR,
                errorMessage = "Network timeout",
            ),
        )

        assertEquals("--", model.latencyText)
        assertEquals("--", model.downloadText)
        assertEquals("--", model.uploadText)
        assertEquals("Network timeout", model.summaryText)
    }

    private fun latencyMeasurement(groupId: String): Measurement {
        return Measurement(
            groupId = groupId,
            type = "latency",
            latencyData = LatencyData(rtt = 42),
        )
    }

    private fun downloadMeasurement(groupId: String): Measurement {
        return Measurement(
            groupId = groupId,
            type = "download",
            uploadDownloadData = UploadDownloadData(bytes = 1_000L, duration = 1_000_000L),
        )
    }

    private fun uploadMeasurement(groupId: String): Measurement {
        return Measurement(
            groupId = groupId,
            type = "upload",
            uploadDownloadData = UploadDownloadData(bytes = 800L, duration = 1_000_000L),
        )
    }
}
