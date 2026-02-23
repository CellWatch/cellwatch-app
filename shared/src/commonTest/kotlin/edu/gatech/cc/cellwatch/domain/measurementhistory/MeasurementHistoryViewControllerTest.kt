package edu.gatech.cc.cellwatch.domain.measurementhistory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementHistoryViewControllerTest {
    @Test
    fun reset_returnsEmptyUnknownState() {
        val controller = MeasurementHistoryViewController()

        val state = controller.reset()

        assertEquals(MeasurementHistoryStateKey.EMPTY, state.status.stateKey)
        assertEquals(MeasurementHistorySyncStateKey.UNKNOWN, state.status.syncStateKey)
        assertEquals(0, state.totalRunCount)
        assertTrue(state.runRows.isEmpty())
        assertNull(state.selectedRun)
    }

    @Test
    fun loadSnapshots_selectsNewestRunByDefault_andBuildsRows() {
        val controller = MeasurementHistoryViewController(maxVisibleRows = 5)

        val state = controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 2000L, latency = "40 ms"),
                snapshot(timestampMs = 3000L, latency = "30 ms"),
                snapshot(timestampMs = 1000L, latency = "50 ms"),
            ),
        )

        assertEquals(3, state.totalRunCount)
        assertEquals(3, state.runRows.size)
        assertEquals(3000L, state.selectedRun?.timestampMs)
        assertTrue(state.runRows.first().selected)
        assertEquals("Run 1: 30 ms latency, 90 Mbps download, 20 Mbps upload", state.runRows.first().summary)
        assertEquals(MeasurementHistoryStateKey.HAS_MEASUREMENT, state.status.stateKey)
    }

    @Test
    fun selectRun_changesSelectedEntryWithoutChangingLatestStatusMetrics() {
        val controller = MeasurementHistoryViewController(maxVisibleRows = 5)
        controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 3000L, latency = "30 ms"),
                snapshot(timestampMs = 2000L, latency = "40 ms"),
                snapshot(timestampMs = 1000L, latency = "50 ms"),
            ),
        )

        val selected = controller.selectRun(2000L)

        assertEquals(2000L, selected.selectedRun?.timestampMs)
        assertTrue(selected.runRows[1].selected)
        // Status card always reflects latest run (timestamp 3000) by contract.
        assertEquals("30 ms", selected.status.latencyText)
    }

    @Test
    fun updatePendingCounts_tracksSyncStateIndependentlyFromRunCount() {
        val controller = MeasurementHistoryViewController(maxVisibleRows = 5)
        controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 5000L),
                snapshot(timestampMs = 4000L),
                snapshot(timestampMs = 3000L),
                snapshot(timestampMs = 2000L),
            ),
        )

        val pending = controller.updatePendingCounts(measurements = 1, submissions = 1)

        assertEquals(4, pending.totalRunCount)
        assertEquals(MeasurementHistorySyncStateKey.PENDING, pending.status.syncStateKey)
        assertEquals("Pending sync queue: 1 measurement record(s), 1 submission record(s).", pending.status.syncSummary)
    }

    @Test
    fun updatePendingCounts_zeroZero_reportsSynced() {
        val controller = MeasurementHistoryViewController()
        controller.loadSnapshots(listOf(snapshot(timestampMs = 1000L, uploaded = "Uploaded")))

        val synced = controller.updatePendingCounts(measurements = 0, submissions = 0)

        assertEquals(MeasurementHistorySyncStateKey.SYNCED, synced.status.syncStateKey)
        assertEquals("All records are synced.", synced.status.syncSummary)
    }

    @Test
    fun updatePendingCounts_negativeValues_areClampedToZero() {
        val controller = MeasurementHistoryViewController()
        controller.loadSnapshots(listOf(snapshot(timestampMs = 1000L)))

        val synced = controller.updatePendingCounts(measurements = -1, submissions = -9)

        assertEquals(MeasurementHistorySyncStateKey.SYNCED, synced.status.syncStateKey)
        assertEquals("All records are synced.", synced.status.syncSummary)
    }

    @Test
    fun loadSnapshots_appliesRowLimit_butKeepsTotalCount() {
        val controller = MeasurementHistoryViewController(maxVisibleRows = 3)
        val state = controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 6000L),
                snapshot(timestampMs = 5000L),
                snapshot(timestampMs = 4000L),
                snapshot(timestampMs = 3000L),
                snapshot(timestampMs = 2000L),
                snapshot(timestampMs = 1000L),
            ),
        )

        assertEquals(6, state.totalRunCount)
        assertEquals(3, state.runRows.size)
        assertEquals(listOf(6000L, 5000L, 4000L), state.runRows.map { it.timestampMs })
    }

    @Test
    fun loadSnapshots_whenPreviouslySelectedMissing_resetsSelectionToNewest() {
        val controller = MeasurementHistoryViewController()
        controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 5000L),
                snapshot(timestampMs = 4000L),
            ),
        )
        controller.selectRun(4000L)

        val state = controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 9000L),
                snapshot(timestampMs = 8000L),
            ),
        )

        assertEquals(9000L, state.selectedRun?.timestampMs)
        assertTrue(state.runRows.first().selected)
    }

    @Test
    fun selectRun_unknownTimestamp_keepsCurrentSelection() {
        val controller = MeasurementHistoryViewController()
        controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 5000L),
                snapshot(timestampMs = 4000L),
            ),
        )

        val state = controller.selectRun(1234L)

        assertEquals(5000L, state.selectedRun?.timestampMs)
    }

    @Test
    fun statusUsesLatestFallback_whenNoRecentMeasurementsProvided() {
        val controller = MeasurementHistoryViewController()
        val state = controller.loadSnapshots(
            listOf(
                snapshot(
                    timestampMs = 5000L,
                    latency = "44 ms",
                    download = "88 Mbps",
                    upload = "22 Mbps",
                    uploaded = "Pending sync",
                    detail = "Measurement complete. Results saved and sync attempted.",
                ),
            ),
        )

        assertEquals(MeasurementHistoryStateKey.HAS_MEASUREMENT, state.status.stateKey)
        assertEquals("44 ms", state.status.latencyText)
        assertEquals("88 Mbps", state.status.downloadText)
        assertEquals("22 Mbps", state.status.uploadText)
        assertEquals("Pending sync", state.status.uploadedText)
        assertEquals("Measurement complete. Results saved and sync attempted.", state.status.detail)
    }

    @Test
    fun selectedRunIncludesRichDetailForUiFormatting() {
        val controller = MeasurementHistoryViewController()
        val state = controller.loadSnapshots(
            listOf(
                snapshot(timestampMs = 7777L, detail = "Detail payload"),
            ),
        )

        val selected = assertNotNull(state.selectedRun)
        assertEquals(7777L, selected.timestampMs)
        assertEquals("Detail payload", selected.detail)
    }

    private fun snapshot(
        timestampMs: Long,
        latency: String = "30 ms",
        download: String = "90 Mbps",
        upload: String = "20 Mbps",
        uploaded: String = "Pending sync",
        detail: String = "Measurement complete. Results saved and sync attempted.",
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
