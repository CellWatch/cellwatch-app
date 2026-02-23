package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapHomeViewControllerTest {
    private val controller = MapHomeViewController()

    @Test
    fun present_notOnboarded_requiresProfileCompletion() {
        val state = controller.present(
            MapHomeInput(
                onboardingComplete = false,
                recentRunCount = 0,
                pendingCountsKnown = false,
                pendingMeasurements = 0,
                pendingSubmissions = 0,
            ),
        )

        assertEquals("CellWatch", state.title)
        assertEquals("Map home", state.subtitle)
        assertEquals("No saved measurements yet. Take a measurement to populate the map.", state.mapPanelBody)
        assertEquals("Complete your profile before taking a measurement.", state.statusText)
        assertEquals(MapHomeSyncStateKey.UNKNOWN, state.syncStateKey)
        assertEquals("Sync status unknown. Open History to refresh.", state.syncSummary)
        assertFalse(state.canStartMeasurement)
    }

    @Test
    fun present_onboardedWithoutRuns_showsReadyState() {
        val state = controller.present(
            MapHomeInput(
                onboardingComplete = true,
                recentRunCount = 0,
                pendingCountsKnown = true,
                pendingMeasurements = 0,
                pendingSubmissions = 0,
            ),
        )

        assertEquals("Profile saved. Start a measurement when ready.", state.statusText)
        assertEquals(MapHomeSyncStateKey.SYNCED, state.syncStateKey)
        assertEquals("All records are synced.", state.syncSummary)
        assertTrue(state.canStartMeasurement)
    }

    @Test
    fun present_withSyncedHistory_reportsSyncedSummary() {
        val state = controller.present(
            MapHomeInput(
                onboardingComplete = true,
                recentRunCount = 3,
                pendingCountsKnown = true,
                pendingMeasurements = 0,
                pendingSubmissions = 0,
            ),
        )

        assertEquals("Showing 3 saved runs on the map (newest first).", state.mapPanelBody)
        assertEquals("Recent measurements available and synced.", state.statusText)
        assertEquals(MapHomeSyncStateKey.SYNCED, state.syncStateKey)
    }

    @Test
    fun present_withPendingHistory_reportsPendingSummary() {
        val state = controller.present(
            MapHomeInput(
                onboardingComplete = true,
                recentRunCount = 2,
                pendingCountsKnown = true,
                pendingMeasurements = 1,
                pendingSubmissions = 2,
            ),
        )

        assertEquals(MapHomeSyncStateKey.PENDING, state.syncStateKey)
        assertEquals(
            "Pending sync queue: 1 measurement record(s), 2 submission record(s).",
            state.syncSummary,
        )
        assertEquals("Recent measurements available. Some uploads are pending.", state.statusText)
    }

    @Test
    fun present_clampsNegativeInputs() {
        val state = controller.present(
            MapHomeInput(
                onboardingComplete = true,
                recentRunCount = -4,
                pendingCountsKnown = true,
                pendingMeasurements = -1,
                pendingSubmissions = -5,
            ),
        )

        assertEquals("No saved measurements yet. Take a measurement to populate the map.", state.mapPanelBody)
        assertEquals(MapHomeSyncStateKey.SYNCED, state.syncStateKey)
        assertEquals("All records are synced.", state.syncSummary)
    }
}
