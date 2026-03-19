package edu.gatech.cc.cellwatch.androidtestapp

import android.os.Looper
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MainActivityUiSmokeTest {

    @Test
    fun mapStartSharedSliceButton_click_updatesStatusWithRenderedResult() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java,
        ).putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_FULL_HARNESS)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        val button = activity.findViewById<Button>(MainActivity.RUN_SHARED_SLICE_BUTTON_ID)
        val status = activity.findViewById<TextView>(MainActivity.STATUS_TEXT_VIEW_ID)

        assertNotNull(button)
        assertNotNull(status)
        button.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)

        val rendered = status.text?.toString().orEmpty()
        assertTrue(rendered.isNotBlank())
        assertTrue(rendered.contains("smokeEnvelope scenario=map-start-sync"))
    }
}
