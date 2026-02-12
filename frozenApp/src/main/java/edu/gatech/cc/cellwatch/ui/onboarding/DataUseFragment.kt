package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.databinding.FragmentDataUseBinding

class DataUseFragment : Fragment() {
    private lateinit var binding: FragmentDataUseBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDataUseBinding.inflate(inflater, container, false)

        // enable clicking on link to show privacy policy
        binding.readPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()

        return binding.root
    }
}
