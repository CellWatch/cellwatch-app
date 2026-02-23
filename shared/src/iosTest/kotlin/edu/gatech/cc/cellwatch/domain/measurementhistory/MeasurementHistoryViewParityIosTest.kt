package edu.gatech.cc.cellwatch.domain.measurementhistory

import kotlin.test.Test

class MeasurementHistoryViewParityIosTest {
    @Test
    fun seededScenarioParity_matchesCommonContract() {
        MeasurementHistoryViewParityContract.assertSeededScenarioParity()
    }
}
