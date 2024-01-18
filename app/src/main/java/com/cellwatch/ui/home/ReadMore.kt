package com.cellwatch.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.cellwatch.R

class ReadMore : Fragment() {
    var goHomeButton: Button? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_readmore, container, false)
        goHomeButton = rootView.findViewById<View>(R.id.button3) as Button
        goHomeButton!!.setOnClickListener { //Simple navigation change instead of navgraph
            val fragmentTransaction = activity
                ?.supportFragmentManager?.beginTransaction()
            fragmentTransaction?.replace(R.id.content_frame, HomeFragment())
            fragmentTransaction?.commit()
        }
        return rootView
    }
}