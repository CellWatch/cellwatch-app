package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.databinding.FragmentReadmoreBinding

class ReadMoreFragment : Fragment() {
    interface ReadMoreInteractionListener {
        fun onClose()
    }

    private lateinit var binding: FragmentReadmoreBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentReadmoreBinding.inflate(inflater, container, false)
        val listener = if (activity is ReadMoreInteractionListener) {
            activity as ReadMoreInteractionListener
        } else {
            throw RuntimeException("HomeFragment requires a parent activity that is a ReadMoreInteractionListener")
        }

        binding.exitButtonBottom.setOnClickListener { listener.onClose() }
        binding.buttonExitTop.setOnClickListener { listener.onClose() }

        return binding.root
    }
}