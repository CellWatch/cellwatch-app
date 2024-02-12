package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R

class ReadMoreFragment : Fragment() {
    private var goHomeButtonTop: Button? = null
    private var goHomeButtonBottom: Button? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_readmore, container, false)
        goHomeButtonBottom = rootView.findViewById<View>(R.id.exitButtonBottom) as Button
        goHomeButtonBottom!!.setOnClickListener { //Simple navigation change instead of navgraph
            returnToHome()
        }

        goHomeButtonTop = rootView.findViewById<View>(R.id.buttonExitTop) as Button
        goHomeButtonTop!!.setOnClickListener { //Simple navigation change instead of navgraph
            returnToHome()
        }

        return rootView
    }


    private fun returnToHome() {
        val fragmentTransaction = activity
            ?.supportFragmentManager?.beginTransaction()
        fragmentTransaction?.replace(R.id.content_frame, HomeFragment())
        fragmentTransaction?.commit()
    }
}