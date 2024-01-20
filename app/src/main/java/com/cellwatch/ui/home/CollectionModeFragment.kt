package com.cellwatch.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cellwatch.R

class CollectionModeFragment : Fragment() {

    //TODO Do something with the FrameLayout that is selected
    private lateinit var flFCCChallengeMode: FrameLayout
    private lateinit var flTestingMode: FrameLayout
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_collectionmode, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the TextView and make it clickable
        val tvReadPrivacyPolicy = view.findViewById<LinearLayout>(R.id.LLReadPrivacyPolicy)
        tvReadPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyText()
        }

        flFCCChallengeMode = view.findViewById(R.id.flFCCChallengeMode)
        flTestingMode = view.findViewById(R.id.flTestingMode)

        flFCCChallengeMode.setOnClickListener {
            it.isSelected = true
            flTestingMode.isSelected = false
        }

        flTestingMode.setOnClickListener {
            it.isSelected = true
            flFCCChallengeMode.isSelected = false
        }
    }

    private fun showPrivacyPolicyText() {
        //TODO Display Privacy Policy

        //Placeholder toast
        Toast.makeText(
            context,
            "Placeholder text.",
            Toast.LENGTH_LONG
        ).show()
    }
}