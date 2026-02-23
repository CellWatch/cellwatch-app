package edu.gatech.cc.cellwatch.domain.measurementhistory

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementHistoryStatusUseCaseTest {
    private val useCase = MeasurementHistoryStatusUseCase()

    @Test
    fun present_withoutMeasurements_reportsEmptyHistory() {
        val model = useCase.present(
            MeasurementHistoryStatusInput(
                recentMeasurements = emptyList(),
            ),
        )

        assertFalse(model.hasMeasurement)
        assertEquals(MeasurementHistoryStateKey.EMPTY, model.stateKey)
        assertEquals(MeasurementHistorySyncStateKey.UNKNOWN, model.syncStateKey)
        assertEquals("No measurements yet", model.title)
        assertEquals("--", model.latencyText)
        assertEquals("Sync status unknown. Tap refresh.", model.syncSummary)
    }

    @Test
    fun present_withLatestGroup_formatsMetricsAndPendingSyncSummary() {
        val model = useCase.present(
            MeasurementHistoryStatusInput(
                recentMeasurements = listOf(
                    measurement(
                        id = "upload-1",
                        groupId = "g-1",
                        type = "upload",
                        timestamp = Instant.parse("2026-02-15T10:00:03Z"),
                        uploadBps = 3_750_000.0,
                    ),
                    measurement(
                        id = "download-1",
                        groupId = "g-1",
                        type = "download",
                        timestamp = Instant.parse("2026-02-15T10:00:02Z"),
                        downloadBps = 12_500_000.0,
                    ),
                    measurement(
                        id = "latency-1",
                        groupId = "g-1",
                        type = "latency",
                        timestamp = Instant.parse("2026-02-15T10:00:01Z"),
                        latencyMicros = 45_000,
                    ),
                ),
                pendingMeasurements = 2,
                pendingSubmissions = 1,
            ),
        )

        assertTrue(model.hasMeasurement)
        assertEquals(MeasurementHistoryStateKey.HAS_MEASUREMENT, model.stateKey)
        assertEquals(MeasurementHistorySyncStateKey.PENDING, model.syncStateKey)
        assertEquals("Latest measurement", model.title)
        assertEquals("45 ms", model.latencyText)
        assertEquals("100 Mbps", model.downloadText)
        assertEquals("30 Mbps", model.uploadText)
        assertEquals("Pending sync", model.uploadedText)
        assertEquals("Pending sync queue: 2 measurement record(s), 1 submission record(s).", model.syncSummary)
    }

    @Test
    fun present_withNoPendingCounts_showsSyncedSummaryWhenZero() {
        val model = useCase.present(
            MeasurementHistoryStatusInput(
                recentMeasurements = listOf(
                    measurement(
                        id = "latency-1",
                        groupId = "g-1",
                        type = "latency",
                        timestamp = Instant.parse("2026-02-15T10:00:01Z"),
                        latencyMicros = 20_000,
                        uploadTime = Instant.parse("2026-02-15T10:01:00Z"),
                    ),
                ),
                pendingMeasurements = 0,
                pendingSubmissions = 0,
            ),
        )

        assertEquals("All records are synced.", model.syncSummary)
        assertEquals(MeasurementHistorySyncStateKey.SYNCED, model.syncStateKey)
    }

    @Test
    fun present_usesFallbackMetricsWhenRecentMeasurementsMissing() {
        val model = useCase.present(
            MeasurementHistoryStatusInput(
                fallbackLatencyText = "31 ms",
                fallbackDownloadText = "88 Mbps",
                fallbackUploadText = "22 Mbps",
                fallbackUploadedText = "Pending sync",
                fallbackDetailText = "Measurement complete. Results saved and sync attempted.",
                pendingMeasurements = 1,
                pendingSubmissions = 0,
            ),
        )

        assertTrue(model.hasMeasurement)
        assertEquals(MeasurementHistoryStateKey.HAS_MEASUREMENT, model.stateKey)
        assertEquals(MeasurementHistorySyncStateKey.PENDING, model.syncStateKey)
        assertEquals("Latest measurement", model.title)
        assertEquals("31 ms", model.latencyText)
        assertEquals("88 Mbps", model.downloadText)
        assertEquals("22 Mbps", model.uploadText)
        assertEquals("Pending sync", model.uploadedText)
    }

    private fun measurement(
        id: String,
        groupId: String,
        type: String,
        timestamp: Instant,
        latencyMicros: Long? = null,
        downloadBps: Double? = null,
        uploadBps: Double? = null,
        uploadTime: Instant? = null,
    ): Measurement {
        val throughputBps = downloadBps ?: uploadBps
        return Measurement(
            id = id,
            groupId = groupId,
            type = type,
            timestamp = timestamp,
            latencyData = latencyMicros?.let { LatencyData(rtt = it.toInt()) },
            uploadDownloadData = throughputBps?.let { UploadDownloadData(bytesPerSec = it) },
            uploadTime = uploadTime,
        )
    }
}
