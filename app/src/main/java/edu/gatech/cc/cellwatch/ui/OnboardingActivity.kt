package edu.gatech.cc.cellwatch.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.databinding.ActivityHomeBinding
import edu.gatech.cc.cellwatch.ui.onboarding.CollectionModeFragment
import edu.gatech.cc.cellwatch.ui.onboarding.DataUseFragment
import edu.gatech.cc.cellwatch.ui.onboarding.FCCInfoFragment
import edu.gatech.cc.cellwatch.ui.onboarding.HomeFragment
import edu.gatech.cc.cellwatch.ui.onboarding.SettingsSetupFragment
import edu.gatech.cc.cellwatch.ui.onboarding.viewmodels.OnboardingViewModel
import edu.gatech.cc.cellwatch.ui.onboarding.viewmodels.OnboardingViewModelFactory

class OnboardingActivity : AppCompatActivity(), HomeFragment.OnMoreInfoSelectedListener, SettingsSetupFragment.OnPermissionsHandledListener  {
    private val TAG = this::class.simpleName

    private val model: OnboardingViewModel by viewModels {
        OnboardingViewModelFactory(CellWatchApp.localDataStore)
    }

    private var currentPosition = 0
    private val fragments = listOf(
        HomeFragment(),
        DataUseFragment(),
        CollectionModeFragment(),
        FCCInfoFragment(),
        SettingsSetupFragment()
    )

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragments.first())
            .commit()

        updateNav(true)

        binding.backArrow.setOnClickListener {
            if (currentPosition > 0) {
                currentPosition--
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, fragments[currentPosition])
                    .commit()
                updateNav()
                Log.d(TAG, "Previous fragment is $currentPosition")
            }
        }

        binding.forwardArrow.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            val isValidated = if (currentFragment is FCCInfoFragment) {
                currentFragment.validateInputs()
            } else {
                true
            }

            if (currentFragment is CollectionModeFragment) {
                if(currentFragment.retrieveSelection()) {
                    currentPosition+=2
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.content_frame, fragments[currentPosition])
                        .commit()
                } else {
                    currentPosition++
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.content_frame, fragments[currentPosition])
                        .commit()
                }
            } else {
                if (isValidated && currentPosition < fragments.size - 1) {
                    currentPosition++
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.content_frame, fragments[currentPosition])
                        .commit()
                }
            }
            updateNav()
        }

        observeDeviceId()
    }

    private fun observeDeviceId() {
        model.getDeviceId().observe(this) { deviceId ->
            Log.d(TAG, "deviceId = $deviceId")
        }
    }

    private fun updateNav(visible: Boolean = true) {
        binding.nav.isVisible = visible
        if (!visible) {
            return
        }

        binding.backArrow.visibility = if (currentPosition > 0) View.VISIBLE else View.INVISIBLE
        binding.forwardArrow.visibility = if (currentPosition < fragments.size - 1) View.VISIBLE else View.INVISIBLE

        val activeDot = when (currentPosition) {
            0 -> binding.dot1
            1 -> binding.dot2
            2, 3 -> binding.dot3
            4 -> binding.dot4
            else -> throw RuntimeException("position $currentPosition out of bounds")
        }

        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4).forEach {
            it.setColorFilter(getColor(if (it == activeDot) R.color.cw_blue else R.color.cw_grey_light))
        }
    }

    override fun onPermissionsHandled() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onMoreInfoSelected(visible: Boolean) {
        updateNav(visible)
    }
}