package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.databinding.FragmentCollectionmodeBinding

class CollectionModeFragment : Fragment() {
    private lateinit var binding: FragmentCollectionmodeBinding

    private var fccMode = false
        set(v) {
            field = v

            binding.flFCCChallengeMode.isSelected = v
            binding.fccModeCheck.isVisible = v
            binding.flTestingMode.isSelected = !v
            binding.testingModeCheck.isVisible = !v
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCollectionmodeBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.LLReadPrivacyPolicy.setOnClickListener { showPrivacyPolicyText() }
        binding.flFCCChallengeMode.setOnClickListener { fccMode = true }
        binding.flTestingMode.setOnClickListener { fccMode = false }
        fccMode = true
    }

    private fun showPrivacyPolicyText() {
        //TODO Display Privacy Policy

        Toast.makeText(
            context,
            "Placeholder text.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun retrieveSelection(): Boolean {
        //True for flTestingMode, False for flFCCChallengeMode and invalid states
        return !fccMode
    }
}