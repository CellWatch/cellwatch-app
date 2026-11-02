package edu.gatech.cc.cellwatch.ui

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.data.local.CellWatchDatabase
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Shared setup for the UI tests. They run in the app's own process, so they set the app up through
 * the same repositories its screens use. Contact details are the examples from §5.1.1 of the FCC's
 * mobile speed test data spec (July 28, 2025).
 */
object UiTestSupport {
    const val NAME = "Jane Broadband"
    const val PHONE = "888-225-5322"
    const val EMAIL = "jane.broadband@fcc.gov"

    private val SERVERS = listOf("s1-fcc-north-carolina.mozark.ai")

    /** Long enough for a whole test to run offline, where each of its three parts takes a minute. */
    const val MEASUREMENT_TIMEOUT_MS = 300_000L

    val settings get() = CellWatchApp.settingsRepository

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Skips a test that starts a real measurement unless the device has no network. Offline, the
     * server lookup fails at once and each part of the test is saved as failed; online, the test
     * would measure against the real test servers.
     */
    fun assumeOffline() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        assumeTrue("starts a real measurement, so it only runs with no network", connectivity.activeNetwork == null)
    }

    /**
     * Where the phone is, waiting up to a minute for a fix, or skips the test if there's none. A
     * test's measurements wait for fresh fixes without a time limit, so they would otherwise hang.
     * On an emulator, keep sending one (`adb emu geo fix <lon> <lat>`, every second) while testing.
     */
    fun phoneLocation(): android.location.Location {
        val client = LocationServices.getFusedLocationProviderClient(context)
        var location = runCatching { Tasks.await(client.lastLocation, 10, TimeUnit.SECONDS) }.getOrNull()
        repeat(2) {
            if (location == null) {
                location = runCatching {
                    Tasks.await(client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null), 30, TimeUnit.SECONDS)
                }.getOrNull()
            }
        }
        assumeTrue("the phone has no location fix", location != null)
        return location!!
    }

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Location without phone state, which the app also asks for during onboarding. */
    fun grantLocationOnly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    /** Runs [command] as the shell user and returns what it printed. */
    fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    /** The package of the window in front, which may be another app's, such as Android's settings. */
    fun foregroundPackage(): String? =
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()

    /** Android's own Back, which also works on another app's window. */
    fun pressSystemBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * Waits for another app's window (Android's settings or permission dialog), then goes Back,
     * giving the app time to come to the front before going Back again.
     */
    fun leaveTheOtherApp() {
        eventually(10_000) { assertNotEquals(context.packageName, foregroundPackage()) }
        repeat(3) {
            pressSystemBack()
            val inFront = runCatching { eventually(5_000) { assertEquals(context.packageName, foregroundPackage()) } }
            if (inFront.isSuccess) return
        }
        fail("still in ${foregroundPackage()}")
    }

    /**
     * Answers the app's next request to start an activity for [action] with [result] instead of
     * starting it. Remove the returned monitor with Instrumentation.removeMonitor.
     */
    fun stubActivity(action: String, result: Instrumentation.ActivityResult): Instrumentation.ActivityMonitor {
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == action) result else null
        }
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor)
        return monitor
    }

    /** The app's open activities of [type]. */
    fun <T : Activity> openActivities(type: Class<T>): List<T> {
        val open = mutableListOf<Activity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)) {
                open += monitor.getActivitiesInStage(stage)
            }
        }
        return open.filterIsInstance(type)
    }

    /** A first run: testing mode, no contact details, nothing acknowledged, onboarding not done. */
    fun resetSettings() = runBlocking {
        // Testing mode first, as the repository refuses to blank contact details in FCC mode.
        settings.setCollectionMode(CollectionMode.TESTING)
        settings.setFccSharingAcknowledged(false)
        settings.setName("")
        settings.setPhoneNumber("")
        settings.setEmail("")
        settings.setOnboardingComplete(false)
    }

    /** Contact details saved and acknowledged, without switching to FCC Challenge mode. */
    fun saveContactDetails(acknowledged: Boolean = true) = runBlocking {
        settings.setName(NAME)
        settings.setPhoneNumber(PHONE)
        settings.setEmail(EMAIL)
        settings.setFccSharingAcknowledged(acknowledged)
    }

    /** Onboarding done in testing mode. */
    fun finishOnboarding() = setting { settings.setOnboardingComplete(true) }

    /** Onboarding done in FCC Challenge mode. */
    fun useFccChallengeMode() = runBlocking {
        saveContactDetails()
        settings.setCollectionMode(CollectionMode.FCC_CHALLENGE)
        settings.setOnboardingComplete(true)
    }

    fun <T> setting(get: suspend () -> T): T = runBlocking { get() }

    /** Empties the test history. */
    fun clearHistory() = runBlocking {
        val db = CellWatchDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
        db.measurementDao().deleteAllMeasurements()
        db.fccSubmissionDao().deleteAllFccSubmissions()
    }

    /** Saves measurements and a submission the way MeasurementManager does at the end of a test. */
    fun saveTest(measurements: List<Measurement>, submission: FccSubmission? = null) = runBlocking {
        measurements.forEach { CellWatchApp.measurementRepository.insertMeasurement(it) }
        submission?.let { CellWatchApp.measurementRepository.insertFccSubmission(it) }
    }

    /**
     * A measurement with the result values of the spec's examples: a 30000 µs round trip
     * (§5.1.6), and 21573627.6 and 2649671.8 bytes a second (§5.1.4 and §5.1.5), which the app
     * shows as 30 ms, 173 Mbps and 21 Mbps. Like every measurement the app records, it names its
     * test server (Room can't store a missing server list); this one is §5.1.4's targets example.
     */
    fun measurement(type: String, groupId: String, start: Instant, success: Boolean = true) = Measurement(
        id = "$groupId-$type",
        groupId = groupId,
        provider = "acme wireless",
        type = type,
        timestamp = start,
        success = success,
        networkRoaming = false,
        uploadDownloadData = when (type) {
            "download" -> UploadDownloadData(bytes = 107_995_136, duration = 5_005_000, bytesPerSec = 21_573_627.6, servers = SERVERS)
            "upload" -> UploadDownloadData(bytes = 14_157_824, duration = 5_343_000, bytesPerSec = 2_649_671.8, servers = SERVERS)
            else -> null
        },
        latencyData = if (type == "latency") {
            LatencyData(rtt = 30_000, jitter = 8_000, sent = 200, received = 200, servers = SERVERS)
        } else {
            null
        },
        locations = listOf(
            Location(timestamp = start, lat = 38.8860976, lon = -76.9962846),
            Location(timestamp = start + 5.seconds, lat = 38.8861234, lon = -76.9960011),
        ),
        // The serving LTE cell and a secondary 5G cell, which the app shows as 4G/5G.
        cells = listOf(
            Cell(timestamp = start, networkGeneration = "4G", networkSubtype = "LTE", cellConnection = 1),
            Cell(timestamp = start, networkGeneration = "5G", networkSubtype = "NRNSA", cellConnection = 2),
        ),
        connectionType = NetworkConnectionType.CELLULAR,
        cellularDataEnabled = true,
    )

    fun submission(groupId: String, inVehicle: Boolean) =
        FccSubmission(id = groupId, inVehicle = inVehicle, externalAntenna = false, deviceType = "Android")

    /**
     * Retries [check] until it passes or [timeoutMs] runs out. Screens load settings and history
     * in coroutines that Espresso doesn't wait for.
     */
    fun eventually(timeoutMs: Long = 5_000, check: () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                check()
                return
            } catch (t: Throwable) {
                if (System.currentTimeMillis() > deadline) throw t
                Thread.sleep(100)
            }
        }
    }

    /** Matches a RecyclerView whose item at [position] matches [itemMatcher]. */
    fun atPosition(position: Int, itemMatcher: Matcher<View>): Matcher<View> =
        object : BoundedMatcher<View, RecyclerView>(RecyclerView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("has item at position $position: ")
                itemMatcher.describeTo(description)
            }

            override fun matchesSafely(view: RecyclerView): Boolean {
                val holder = view.findViewHolderForAdapterPosition(position) ?: return false
                return itemMatcher.matches(holder.itemView)
            }
        }

    /** Finishes every activity still open, such as one a test's screen started. */
    fun finishAllActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            val open = mutableSetOf<Activity>()
            for (stage in listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)) {
                open += monitor.getActivitiesInStage(stage)
            }
            open.forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }
}
