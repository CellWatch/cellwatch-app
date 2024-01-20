package com.cellwatch.ui.home

import SettingsSetupFragment
import com.cellwatch.R
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity



class HomeActivity : AppCompatActivity() {

    // Keep track of the current position in the series of setup menus
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

        // Initialize the first fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragments.first())
            .commit()

        backButton = findViewById(R.id.back_arrow)
        nextButton = findViewById(R.id.forward_arrow)

        // Initially, the back arrow should not be visible on the first fragment
        backButton.visibility = View.GONE

        // The next arrow should not be visible on the last fragment
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

    private fun updateArrowVisibility() {
        backButton.visibility = if (currentPosition == 0) View.GONE else View.VISIBLE
        nextButton.visibility = if (currentPosition == fragments.size - 1) View.GONE else View.VISIBLE
    }
}
