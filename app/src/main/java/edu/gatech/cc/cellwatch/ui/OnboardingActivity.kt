package edu.gatech.cc.cellwatch.ui

import android.content.Intent
import edu.gatech.cc.cellwatch.ui.onboarding.SettingsSetupFragment
import edu.gatech.cc.cellwatch.R
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.ui.onboarding.CollectionModeFragment
import edu.gatech.cc.cellwatch.ui.onboarding.DataUseFragment
import edu.gatech.cc.cellwatch.ui.onboarding.FCCInfoFragment
import edu.gatech.cc.cellwatch.ui.onboarding.HomeFragment
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

    private lateinit var backButton: ImageView
    private lateinit var nextButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragments.first())
            .commit()

        backButton = findViewById(R.id.back_arrow)
        nextButton = findViewById(R.id.forward_arrow)

        backButton.visibility = View.GONE
        nextButton.visibility = if (currentPosition == fragments.size - 1) View.GONE else View.VISIBLE

        backButton.setOnClickListener {
            if (currentPosition > 0) {
                currentPosition--
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, fragments[currentPosition])
                    .commit()
                updateArrowVisibility()
                Log.d(TAG, "Previous fragment is $currentPosition")
            }
        }

        nextButton.setOnClickListener {
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
            updateArrowVisibility()
        }

        observeDeviceId()
    }

    private fun observeDeviceId() {
        model.getDeviceId().observe(this) { deviceId ->
            Log.d(TAG, "deviceId = $deviceId")
            Toast.makeText(this, "Your deviceId is: $deviceId", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateArrowVisibility(visible: Boolean = true) {
        if (!visible) {
            backButton.visibility = View.GONE
            nextButton.visibility = View.GONE
        } else {
            backButton.visibility = if (currentPosition == 0) View.GONE else View.VISIBLE
            nextButton.visibility = if (currentPosition == fragments.size - 1) View.GONE else View.VISIBLE
        }
    }

    override fun onPermissionsHandled() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onMoreInfoSelected(visible: Boolean) {
        updateArrowVisibility(visible)
    }
}