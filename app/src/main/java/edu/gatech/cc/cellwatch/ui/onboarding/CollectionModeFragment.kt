package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.databinding.FragmentCollectionmodeBinding

class CollectionModeFragment : Fragment() {
    interface CollectionModeInteractionListener {
        fun onCollectionModeChanged(mode: CollectionMode)
    }

    private lateinit var binding: FragmentCollectionmodeBinding
    private lateinit var listener: CollectionModeInteractionListener

    private var fccMode = true
        set(v) {
            field = v

            binding.flFCCChallengeMode.isSelected = v
            binding.fccModeCheck.isVisible = v
            binding.flTestingMode.isSelected = !v
            binding.testingModeCheck.isVisible = !v
            listener.onCollectionModeChanged(if (v) CollectionMode.FCC_CHALLENGE else CollectionMode.TESTING)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCollectionmodeBinding.inflate(inflater, container, false)
        listener = if (activity is CollectionModeInteractionListener) {
            activity as CollectionModeInteractionListener
        } else {
            throw RuntimeException("HomeFragment requires a parent activity that is a CollectionModeInteractionListener")
        }

        fccMode = savedInstanceState?.getBoolean(STATE_FCC_MODE) ?: fccMode
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.flFCCChallengeMode.setOnClickListener { fccMode = true }
        binding.flTestingMode.setOnClickListener { fccMode = false }

        // enable clicking on link to show privacy policy
        binding.readPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_FCC_MODE, fccMode)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val STATE_FCC_MODE = "fcc_mode"
    }
}