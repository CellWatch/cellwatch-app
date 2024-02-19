package edu.gatech.cc.cellwatch.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {
    private lateinit var shareArray: Array<String>
    private var shareSpinner: Spinner? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)

        shareSpinner = rootView.findViewById(R.id.data_share_spinner)
        shareArray = resources.getStringArray(R.array.data_sharing_options_array)
        shareSpinner?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, shareArray)


        val settingsEditButton = rootView.findViewById<AppCompatImageButton>(R.id.settingsEditButton)
        val saveButton = rootView.findViewById<AppCompatButton>(R.id.submit_edit)

        val nameEditText = rootView.findViewById<TextInputEditText>(R.id.nameEditText)
        val emailEditText = rootView.findViewById<TextInputEditText>(R.id.emailEditText)
        val phoneEditText = rootView.findViewById<TextInputEditText>(R.id.phoneEditText)

        //TODO Load user name/email/phone/FCC Data Collection on fragment start
        /*
        nameEditText.setText()
        emailEditText.setText()
        phoneEditText.setText()
        shareSpinner?.setSelection(0)
         */

        settingsEditButton.setOnClickListener {
            saveButton.visibility = View.VISIBLE
            settingsEditButton.visibility = View.GONE

            nameEditText.isEnabled = true
            emailEditText.isEnabled = true
            phoneEditText.isEnabled = true
        }

        saveButton.setOnClickListener {
            saveButton.visibility = View.GONE
            settingsEditButton.visibility = View.VISIBLE

            nameEditText.isEnabled = false
            emailEditText.isEnabled = false
            phoneEditText.isEnabled = false

            //TODO save edited user name/email/phone on saveButton click
        }

        return rootView
    }
}
