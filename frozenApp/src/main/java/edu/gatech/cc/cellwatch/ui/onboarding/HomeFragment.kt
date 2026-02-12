package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    interface HomeInteractionListener {
        fun onMoreInfoClicked()
    }

    private lateinit var binding: FragmentHomeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        val listener = if (activity is HomeInteractionListener) {
            activity as HomeInteractionListener
        } else {
            throw RuntimeException("HomeFragment requires a parent activity that is a HomeInteractionListener")
        }

        binding.buttonReadmore.setOnClickListener { listener.onMoreInfoClicked() }
        return binding.root
    }
}
