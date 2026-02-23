package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.domain.maphome.MapHomeInput
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeSyncStateKey
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeViewController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapHomeViewParityTest {
    @Test
    fun baselineContract_matchesSharedBehavior() {
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
        assertTrue(synced.canStartMeasurement)
    }
}
