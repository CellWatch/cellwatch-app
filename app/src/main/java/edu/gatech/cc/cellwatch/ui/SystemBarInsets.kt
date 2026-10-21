package edu.gatech.cc.cellwatch.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Apps that target Android 15 or later are drawn edge to edge: behind the status bar, the navigation
 * bar and display cutouts, and Android 16 removed the opt-out. None of the app's layouts were written
 * for that, so the toolbars sat under the status bar and the bottom buttons under the navigation bar.
 *
 * This pads every activity's content by those insets, which puts it back where it was, and by the
 * keyboard unless the activity pans for it (the map does). The theme's status bar colour no longer
 * applies, so the bar icons are made dark to suit the app's light screens.
 */
object SystemBarInsets : Application.ActivityLifecycleCallbacks {
    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        val window = activity.window
        val content = activity.findViewById<View>(android.R.id.content) ?: return

        val adjust = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        val pansForKeyboard = adjust == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
            if (pansForKeyboard) 0 else WindowInsetsCompat.Type.ime()

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(types)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
