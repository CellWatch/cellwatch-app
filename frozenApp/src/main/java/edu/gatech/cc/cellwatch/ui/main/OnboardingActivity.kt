package edu.gatech.cc.cellwatch.ui.main

import SettingsSetupFragment
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.databinding.ActivityHomeBinding
import edu.gatech.cc.cellwatch.ui.onboarding.CollectionModeFragment
import edu.gatech.cc.cellwatch.ui.onboarding.DataUseFragment
import edu.gatech.cc.cellwatch.ui.onboarding.FCCInfoFragment
import edu.gatech.cc.cellwatch.ui.onboarding.HomeFragment
import edu.gatech.cc.cellwatch.ui.onboarding.ReadMoreFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OnboardingActivity : AppCompatActivity(),
    HomeFragment.HomeInteractionListener,
    ReadMoreFragment.ReadMoreInteractionListener,
    CollectionModeFragment.CollectionModeInteractionListener,
    SettingsSetupFragment.OnPermissionsHandledListener
{
    private lateinit var binding: ActivityHomeBinding
    private var screen = Screen.WELCOME
    private var collectionMode: CollectionMode = CollectionMode.FCC_CHALLENGE
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gestureDetector = GestureDetector(this, SwipeGestureListener())

        supportFragmentManager.addFragmentOnAttachListener { _, fragment -> handleFragmentChange(fragment) }
        supportFragmentManager.addOnBackStackChangedListener { handleFragmentChange(supportFragmentManager.findFragmentById(R.id.content_frame)) }

        binding.backArrow.setOnClickListener { goPrev() }
        binding.forwardArrow.setOnClickListener { goNext() }

        val fragment = supportFragmentManager.findFragmentById(R.id.content_frame)
        val s = fragment?.let { Screen.fromFragment(it) }
        if (s == null) {
            setCurrentFragment(screen.toNewFragment(), false)
        } else {
            screen = s
        }

        updateNav(screen.isNavVisible(), screen.toNavPosition())
    }

    override fun onPermissionsHandled() {
        runBlocking { CellWatchApp.settingsRepository.setOnboardingComplete(true) }
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }

    private fun goNext() {
        if (screen == Screen.FCC_INFO) {
            val fragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            if (fragment !is FCCInfoFragment) {
                throw RuntimeException("screen is set to FCC info, but fragment is $fragment")
            }
            if (!fragment.validateInputs()) {
                return
            }
            lifecycleScope.launch {
                fragment.storeData()
                CellWatchApp.settingsRepository.setCollectionMode(collectionMode)
            }
        } else if (screen == Screen.COLLECTION_MODE && collectionMode == CollectionMode.TESTING) {
            lifecycleScope.launch { CellWatchApp.settingsRepository.setCollectionMode(collectionMode) }
        }

        setCurrentFragment(screen.next(collectionMode).toNewFragment())
    }

    private fun goPrev() {
        supportFragmentManager.popBackStack()
    }

    private fun setCurrentFragment(f: Fragment, allowBack: Boolean = true) {
        supportFragmentManager.commit {
            replace(R.id.content_frame, f)
            if (allowBack) addToBackStack(null)
        }
    }

    private fun updateNav(visible: Boolean, position: Int) {
        binding.nav.isVisible = visible
        val activeDot = when (position) {
            1 -> binding.dot1
            2 -> binding.dot2
            3 -> binding.dot3
            4 -> binding.dot4
            else -> throw IllegalArgumentException("nav position $position out of range")
        }

        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4).forEach {
            it.setColorFilter(getColor(if (it == activeDot) R.color.cw_blue else R.color.cw_grey_light))
        }

        binding.backArrow.visibility = if (position > 1) View.VISIBLE else View.INVISIBLE
        binding.forwardArrow.visibility = if (position < 4) View.VISIBLE else View.INVISIBLE
    }

    override fun onMoreInfoClicked() {
        setCurrentFragment(ReadMoreFragment())
    }

    override fun onClose() {
        supportFragmentManager.popBackStack()
    }

    override fun onCollectionModeChanged(mode: CollectionMode) {
        collectionMode = mode
    }

    fun handleFragmentChange(f: Fragment?) {
        val s = f?.let { Screen.fromFragment(it) }
        if (s != null) {
            screen = s
            updateNav(screen.isNavVisible(), screen.toNavPosition())
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                if (kotlin.math.abs(diffX) > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        goPrev()
                    } else {
                        goNext()
                    }
                    return true
                }
            }
            return false
        }
    }

    enum class Screen {
        WELCOME, READ_MORE, DATA_USE, COLLECTION_MODE, FCC_INFO, PERMISSIONS;

        fun next(selectedMode: CollectionMode): Screen = when (this) {
            WELCOME -> DATA_USE
            READ_MORE -> throw RuntimeException("can't go next from read more screen")
            DATA_USE -> COLLECTION_MODE
            COLLECTION_MODE -> if (selectedMode == CollectionMode.FCC_CHALLENGE) FCC_INFO else PERMISSIONS
            FCC_INFO -> PERMISSIONS
            PERMISSIONS -> throw RuntimeException("can't go next from permissions screen")
        }

        fun toNavPosition(): Int = when (this) {
            WELCOME, READ_MORE -> 1
            DATA_USE -> 2
            COLLECTION_MODE, FCC_INFO -> 3
            PERMISSIONS -> 4
        }

        fun isNavVisible(): Boolean = when (this) {
            READ_MORE -> false
            else -> true
        }

        fun toNewFragment(): Fragment = when (this) {
            WELCOME -> HomeFragment()
            READ_MORE -> ReadMoreFragment()
            DATA_USE -> DataUseFragment()
            COLLECTION_MODE -> CollectionModeFragment()
            FCC_INFO -> FCCInfoFragment()
            PERMISSIONS -> SettingsSetupFragment()
        }

        companion object {
            fun fromFragment(f: Fragment): Screen? = when (f) {
                is HomeFragment -> WELCOME
                is ReadMoreFragment -> READ_MORE
                is DataUseFragment -> DATA_USE
                is CollectionModeFragment -> COLLECTION_MODE
                is FCCInfoFragment -> FCC_INFO
                is SettingsSetupFragment -> PERMISSIONS
                else -> null
            }
        }
    }
}