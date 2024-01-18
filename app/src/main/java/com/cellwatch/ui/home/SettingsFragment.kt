package com.cellwatch.ui.home

import com.cellwatch.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    private lateinit var langArray: Array<String>
    private lateinit var shareArray: Array<String>
    var langSpinner: Spinner? = null
    var shareSpinner: Spinner? = null
    var editSettingsButton: ImageButton? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView: View = inflater.inflate(R.layout.fragment_settings, container, false)

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
        editSettingsButton = rootView.findViewById<View>(R.id.settingsEditButton) as ImageButton
        editSettingsButton!!.setOnClickListener {
            //Simple navigation change instead of navgraph
            val fragmentTransaction = activity
                ?.supportFragmentManager?.beginTransaction()
            fragmentTransaction.replace(R.id.content_frame, SettingsEditFragment())
            fragmentTransaction.commit()
            //NK TODO: Change navigation system to Navgraph, and re-implement this
            //    NavHostFragment.findNavController(SettingsFragment.this)
            //            .navigate(R.id.action_settingsFragment_to_settingsEditFragment);
        }
        return rootView
    }
}