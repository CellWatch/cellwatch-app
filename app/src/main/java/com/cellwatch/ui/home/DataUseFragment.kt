package com.cellwatch.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cellwatch.R

class DataUseFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data_use, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvReadPrivacyPolicy = view.findViewById<LinearLayout>(R.id.LLReadPrivacyPolicy)
        tvReadPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyText()
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
