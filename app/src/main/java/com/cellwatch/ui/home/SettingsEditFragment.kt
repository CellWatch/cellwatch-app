package com.cellwatch.ui.home

import com.cellwatch.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment

class SettingsEditFragment : Fragment() {
    private lateinit var langArray: Array<String>
    private lateinit var shareArray: Array<String>
    private var langSpinner: Spinner? = null
    private var shareSpinner: Spinner? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView: View = inflater.inflate(R.layout.fragment_settings_edit, container, false)

        //Create arrayadapter for lang array
        //get language array and spinner
        langSpinner = rootView.findViewById<View>(R.id.lang_spinner) as Spinner
        langArray = resources.getStringArray(R.array.language_options_array)
        // assign an array to the adapter
        val langadapter: ArrayAdapter<String> =
            ArrayAdapter(this.activity!!, R.layout.simple_list_item_1, langArray)
        //set the spinners adapter to the previously created one.
        langSpinner!!.adapter = langadapter

        //Create arrayadapter for share array
        //get share array and spinner
        shareSpinner = rootView.findViewById<View>(R.id.data_share_spinner) as Spinner
        shareArray = resources.getStringArray(R.array.data_sharing_options_array)
        // assign an array to the adapter
        val shareadapter: ArrayAdapter<String> =
            ArrayAdapter(this.activity!!, R.layout.simple_list_item_1, shareArray)
        //set the spinners adapter to the previously created one.
        shareSpinner!!.adapter = shareadapter
        return rootView
    }
}