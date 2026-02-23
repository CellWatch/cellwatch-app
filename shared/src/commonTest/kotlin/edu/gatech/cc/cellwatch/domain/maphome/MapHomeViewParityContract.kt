package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.assertEquals
import kotlin.test.assertTrue

object MapHomeViewParityContract {
    fun assertBaselineParity() {
        val controller = MapHomeViewController()

        val pending = controller.present(
            MapHomeInput(
                onboardingComplete = true,
                recentRunCount = 2,
                pendingCountsKnown = true,
                pendingMeasurements = 1,
                pendingSubmissions = 1,
            ),
        )
        assertEquals(MapHomeSyncStateKey.PENDING, pending.syncStateKey)
        assertEquals("Map home", pending.subtitle)
        assertEquals("Measurement map", pending.mapPanelTitle)
        assertEquals("Recent measurements available. Some uploads are pending.", pending.statusText)

        val synced = controller.present(
            MapHomeInput(
                onboardingComplete = true,
                recentRunCount = 2,
                pendingCountsKnown = true,
                pendingMeasurements = 0,
                pendingSubmissions = 0,
            ),
        )
        assertEquals(MapHomeSyncStateKey.SYNCED, synced.syncStateKey)
        assertEquals("All records are synced.", synced.syncSummary)
        assertTrue(synced.canStartMeasurement)
    }
}
