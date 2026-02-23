package edu.gatech.cc.cellwatch.domain.measurementhistory

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Strict parity contract for platform wrappers. No skip/bypass behavior.
 */
object MeasurementHistoryViewParityContract {
    fun assertSeededScenarioParity() {
        val controller = MeasurementHistoryViewController(maxVisibleRows = 5)

        val snapshots = listOf(
            snapshot(
                timestampMs = 3_000L,
                latency = "30 ms",
                download = "100 Mbps",
                upload = "20 Mbps",
                uploaded = "Pending sync",
                detail = "Measurement complete. Results saved and sync attempted.",
            ),
            snapshot(
                timestampMs = 2_000L,
                latency = "35 ms",
                download = "90 Mbps",
                upload = "18 Mbps",
                uploaded = "Uploaded",
                detail = "Measurement complete. Results saved and synced.",
            ),
            snapshot(
                timestampMs = 1_000L,
                latency = "40 ms",
                download = "80 Mbps",
                upload = "16 Mbps",
                uploaded = "Uploaded",
                detail = "Measurement complete. Results saved and synced.",
            ),
        )

        val loaded = controller.loadSnapshots(snapshots)
        assertEquals(MeasurementHistoryStateKey.HAS_MEASUREMENT, loaded.status.stateKey)
        assertEquals(MeasurementHistorySyncStateKey.UNKNOWN, loaded.status.syncStateKey)
        assertEquals("Latest measurement", loaded.status.title)
        assertEquals("30 ms", loaded.status.latencyText)
        assertEquals("100 Mbps", loaded.status.downloadText)
        assertEquals("20 Mbps", loaded.status.uploadText)
        assertEquals("Pending sync", loaded.status.uploadedText)
        assertEquals("Sync status unknown. Tap refresh.", loaded.status.syncSummary)
        assertEquals(3, loaded.totalRunCount)
        assertEquals(3, loaded.runRows.size)
        assertTrue(loaded.runRows[0].selected)

        val pending = controller.updatePendingCounts(measurements = 1, submissions = 1)
        assertEquals(MeasurementHistorySyncStateKey.PENDING, pending.status.syncStateKey)
        assertEquals("Pending sync queue: 1 measurement record(s), 1 submission record(s).", pending.status.syncSummary)
        assertEquals(3, pending.totalRunCount)

        val synced = controller.updatePendingCounts(measurements = 0, submissions = 0)
        assertEquals(MeasurementHistorySyncStateKey.SYNCED, synced.status.syncStateKey)
        assertEquals("All records are synced.", synced.status.syncSummary)
        assertEquals(3, synced.totalRunCount)

        val selected = controller.selectRun(2_000L)
        val selectedRun = assertNotNull(selected.selectedRun)
        assertEquals(2_000L, selectedRun.timestampMs)
        assertTrue(selected.runRows[1].selected)
        assertFalse(selected.runRows[0].selected)

        // Explicitly lock semantic independence between run count and pending queue.
        assertEquals(3, selected.totalRunCount)
        assertEquals("All records are synced.", selected.status.syncSummary)
    }

    private fun snapshot(
        timestampMs: Long,
        latency: String,
        download: String,
        upload: String,
        uploaded: String,
        detail: String,
    ): MeasurementHistoryRunSnapshot {
        return MeasurementHistoryRunSnapshot(
            timestampMs = timestampMs,
            latency = latency,
            download = download,
            upload = upload,
            uploaded = uploaded,
            detail = detail,
        )
    }
}
