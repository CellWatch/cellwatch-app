package edu.gatech.cc.cellwatch.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.openActivities
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Help screen. */
@RunWith(AndroidJUnit4::class)
class AboutActivityTest {
    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun licencesButtonListsTheLibrariesTheAppUses() {
        ActivityScenario.launch(AboutActivity::class.java).use {
            onView(withId(R.id.show_dep_licenses)).perform(scrollTo(), click())

            eventually { assertTrue(openActivities(OssLicensesMenuActivity::class.java).isNotEmpty()) }
        }
    }
}
