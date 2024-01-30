package com.cellwatch.ui

import android.content.Intent
import com.cellwatch.ui.onboarding.SettingsSetupFragment
import com.cellwatch.R
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.cellwatch.ui.onboarding.CollectionModeFragment
import com.cellwatch.ui.onboarding.DataUseFragment
import com.cellwatch.ui.onboarding.FCCInfoFragment
import com.cellwatch.ui.onboarding.HomeFragment

class OnboardingActivity : AppCompatActivity(), HomeFragment.OnMoreInfoSelectedListener, SettingsSetupFragment.OnPermissionsHandledListener  {

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
            }
        }

        nextButton.setOnClickListener {
            if (currentPosition < fragments.size - 1) {
                currentPosition++
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, fragments[currentPosition])
                    .commit()
                updateArrowVisibility()
            }
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
