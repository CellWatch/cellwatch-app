package edu.gatech.cc.cellwatch.ui.main

import android.content.ComponentName
import android.os.Binder
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementService
import edu.gatech.cc.cellwatch.ui.UiTestSupport.context
import edu.gatech.cc.cellwatch.ui.main.MeasureViewModel.MeasureProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The measuring screen's state, apart from a running test (MeasureFlowTest runs those). It follows
 * MeasurementService through the binder the service hands out when the screen binds to it.
 */
@RunWith(AndroidJUnit4::class)
class MeasureViewModelTest {
    private val service = ComponentName(context, MeasurementService::class.java)

    @Test
    fun aBinderThatIsntTheServicesIsIgnored() {
        val model = MeasureViewModel()

        model.serviceConnection.onServiceConnected(service, Binder())

        assertEquals(MeasureProgress.PRE, model.state.value.progress)
    }

    @Test
    fun losingTheServiceLeavesTheStateAlone() {
        val model = MeasureViewModel()
        model.setInVehicle(true)

        model.serviceConnection.onServiceDisconnected(service)

        assertTrue(model.state.value.inVehicle)
    }

    @Test
    fun resetGoesBackToBeforeATest() {
        val model = MeasureViewModel()
        model.setInVehicle(true)

        model.reset()

        assertEquals(MeasureViewModel.State(MeasureProgress.PRE, null, null, false, null), model.state.value)
    }

    @Test
    fun beforeATestNoStepIsUnderway() {
        val steps = MeasureViewModel().stepProgress()

        assertNull(steps.currentStep)
        assertTrue(steps.statuses.all { it == MeasureStepStatus.PENDING })
    }
}
