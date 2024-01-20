package com.cellwatch.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.cellwatch.R

class HomeFragment : Fragment() {

    private lateinit var langSpinner: Spinner
    private lateinit var moreInfoButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(R.layout.home_fragment, container, false)

        langSpinner = rootView.findViewById(R.id.lang_spinner)
        val langArray = resources.getStringArray(R.array.language_options_array)
        val langAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, langArray)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        langSpinner.adapter = langAdapter

        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                // TODO Change app language to the selected item
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        moreInfoButton = rootView.findViewById(R.id.button_readmore)
        moreInfoButton.setOnClickListener {
            navigateToReadMoreFragment()
        }

        return rootView
    }

    private fun navigateToReadMoreFragment() {
        activity?.supportFragmentManager?.beginTransaction()
            ?.replace(R.id.content_frame, ReadMoreFragment())
            ?.commit()
    }
}
