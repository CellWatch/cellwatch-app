package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunErrorCategory
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewController
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementRunErrorCategoryParityTest {
    private val controller = MeasurementRunViewController()

    @Test
    fun classifyErrorCategory_parityMatrix_matchesSharedContract() {
        assertEquals(
            MeasurementRunErrorCategory.NETWORK,
            controller.onCompleted(
                group = null,
                errorCode = null,
                errorText = "network timeout during upload",
            ).errorCategory,
        )
        assertEquals(
            MeasurementRunErrorCategory.AUTH_CONFIG,
            controller.onCompleted(
                group = null,
                errorCode = 401,
                errorText = "Unauthorized: invalid api key",
            ).errorCategory,
        )
        assertEquals(
            MeasurementRunErrorCategory.SERVER,
            controller.onCompleted(
                group = null,
                errorCode = 500,
                errorText = "Server protocol decode failure",
            ).errorCategory,
        )
        assertEquals(
            MeasurementRunErrorCategory.UNKNOWN,
            controller.onCompleted(
                group = null,
                errorCode = null,
                errorText = "unexpected boom",
            ).errorCategory,
        )
    }
}
