package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R

class HomeFragment : Fragment() {

    interface OnMoreInfoSelectedListener {
        fun onMoreInfoSelected(visible: Boolean)
    }

    private lateinit var moreInfoButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(R.layout.fragment_home, container, false)

        moreInfoButton = rootView.findViewById(R.id.button_readmore)
        moreInfoButton.setOnClickListener {
            navigateToReadMoreFragment()
        }

        return rootView
    }

    private fun navigateToReadMoreFragment() {
        (activity as? OnMoreInfoSelectedListener)?.onMoreInfoSelected(false)
        activity?.supportFragmentManager?.beginTransaction()
            ?.replace(R.id.content_frame, ReadMoreFragment())
            ?.commit()
    }

    override fun onResume() {
        super.onResume()
        (activity as? OnMoreInfoSelectedListener)?.onMoreInfoSelected(true)
    }
}
