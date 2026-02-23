package edu.gatech.cc.cellwatch.domain.measurementhistory

import org.junit.Test

class MeasurementHistoryViewParityAndroidTest {
    @Test
    fun seededScenarioParity_matchesCommonContract() {
        MeasurementHistoryViewParityContract.assertSeededScenarioParity()
    }
}
