package edu.gatech.cc.cellwatch.domain.measurementhistory

import kotlinx.datetime.Clock
import java.io.File
import kotlin.test.Test

class MeasurementHistoryFixtureCaptureJvmTest {
    @Test
    fun captureFixture_whenEnabled_writesFixtureJson() {
        if (System.getenv("CELLWATCH_CAPTURE_HISTORY_FIXTURE") != "1") {
            return
        }

        val output = System.getenv("CELLWATCH_HISTORY_FIXTURE_OUT")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: MeasurementHistoryFixtureSupport.defaultFixtureFile()

        val fixture = MeasurementHistoryFixture(
            metadata = MeasurementHistoryFixtureMetadata(
                fixtureSchemaVersion = 1,
                source = "measurement-history-usecase-capture",
                capturedAtUtc = Clock.System.now().toString(),
                msakClientKmpVersion = MeasurementHistoryFixtureSupport.expectedMsakClientKmpVersion(),
            ),
            scenarios = defaultScenarios(),
        )

        MeasurementHistoryFixtureSupport.save(output, fixture)
    }

    private fun defaultScenarios(): List<MeasurementHistoryFixtureScenario> {
        return listOf(
            MeasurementHistoryFixtureScenario(
                name = "pending_with_recent_group",
                pendingMeasurements = 2,
                pendingSubmissions = 1,
                recentMeasurements = listOf(
                    MeasurementHistoryFixtureMeasurement(
                        id = "upload-1",
                        groupId = "g-1",
                        type = "upload",
                        timestampIso = "2026-02-15T10:00:03Z",
                        bytesPerSec = 3_750_000.0,
                    ),
                    MeasurementHistoryFixtureMeasurement(
                        id = "download-1",
                        groupId = "g-1",
                        type = "download",
                        timestampIso = "2026-02-15T10:00:02Z",
                        bytesPerSec = 12_500_000.0,
                    ),
                    MeasurementHistoryFixtureMeasurement(
                        id = "latency-1",
                        groupId = "g-1",
                        type = "latency",
                        timestampIso = "2026-02-15T10:00:01Z",
                        latencyMicros = 45_000,
                    ),
                ),
                expectedStateKey = MeasurementHistoryStateKey.HAS_MEASUREMENT.name,
                expectedSyncStateKey = MeasurementHistorySyncStateKey.PENDING.name,
                expectedTitle = "Latest measurement",
                expectedLatencyText = "45 ms",
                expectedDownloadText = "100 Mbps",
                expectedUploadText = "30 Mbps",
                expectedUploadedText = "Pending sync",
                expectedSyncSummary = "Pending sync queue: 2 measurement record(s), 1 submission record(s).",
            ),
            MeasurementHistoryFixtureScenario(
                name = "empty_unknown_sync",
                expectedStateKey = MeasurementHistoryStateKey.EMPTY.name,
                expectedSyncStateKey = MeasurementHistorySyncStateKey.UNKNOWN.name,
                expectedTitle = "No measurements yet",
                expectedLatencyText = "--",
                expectedDownloadText = "--",
                expectedUploadText = "--",
                expectedUploadedText = "--",
                expectedSyncSummary = "Sync status unknown. Tap refresh.",
            ),
            MeasurementHistoryFixtureScenario(
                name = "fallback_pending",
                pendingMeasurements = 1,
                pendingSubmissions = 0,
                fallbackLatencyText = "31 ms",
                fallbackDownloadText = "88 Mbps",
                fallbackUploadText = "22 Mbps",
                fallbackUploadedText = "Pending sync",
                fallbackDetailText = "Measurement complete. Results saved and sync attempted.",
                expectedStateKey = MeasurementHistoryStateKey.HAS_MEASUREMENT.name,
                expectedSyncStateKey = MeasurementHistorySyncStateKey.PENDING.name,
                expectedTitle = "Latest measurement",
                expectedLatencyText = "31 ms",
                expectedDownloadText = "88 Mbps",
                expectedUploadText = "22 Mbps",
                expectedUploadedText = "Pending sync",
                expectedSyncSummary = "Pending sync queue: 1 measurement record(s), 0 submission record(s).",
            ),
        )
    }
}
