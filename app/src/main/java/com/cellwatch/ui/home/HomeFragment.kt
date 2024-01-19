package com.cellwatch.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.cellwatch.R

class HomeFragment : Fragment() {
    private lateinit var langArray: Array<String>
    private var langSpinner: Spinner? = null
    private var moreInfoButton: Button? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.home_fragment, container, false)

        //Create arrayadapter for lang array
        //get language array and spinner
        langSpinner = rootView.findViewById<View>(R.id.lang_spinner) as Spinner
        langArray = resources.getStringArray(R.array.language_options_array)
        // assign an array to the adapter
        val langadapter: ArrayAdapter<String> =
            ArrayAdapter(this.activity!!, android.R.layout.simple_list_item_1, langArray)
        //set the spinners adapter to the previously created one.
        langSpinner!!.adapter = langadapter
        moreInfoButton = rootView.findViewById<View>(R.id.button_readmore) as Button
        moreInfoButton!!.setOnClickListener {
            //Simple navigation change instead of navgraph
            val fragmentTransaction = activity
                ?.supportFragmentManager?.beginTransaction()
            fragmentTransaction?.replace(R.id.content_frame, ReadMoreFragment())
            fragmentTransaction?.commit()
            //NK TODO: Change navigation system to Navgraph, and re-implement this
            //    NavHostFragment.findNavController(SettingsFragment.this)
            //            .navigate(R.id.action_settingsFragment_to_settingsEditFragment);
        }
        return rootView
    }
}