package edu.gatech.cc.cellwatch.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var shareArray: Array<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        shareArray = resources.getStringArray(R.array.data_sharing_options_array)
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, shareArray)
        (binding.dataShareMenu.editText as? AutoCompleteTextView)?.setAdapter(adapter)


        //TODO Load user name/email/phone/FCC Data Collection on fragment start
        /*
        nameEditText.setText()
        emailEditText.setText()
        phoneEditText.setText()
        shareSpinner?.setSelection(0)
         */

        binding.settingsEditButton.setOnClickListener { setEditable(true) }

        binding.submitEdit.setOnClickListener {
            setEditable(false)
            //TODO save edited user name/email/phone on submitEdit click
        }

        setEditable(false)

        return binding.root
    }

    private fun setEditable(e: Boolean) {
        binding.settingsEditButton.visibility = if (e) View.INVISIBLE else View.VISIBLE
        binding.submitEdit.isVisible = e
        binding.nameEditText.isEnabled = e
        binding.emailEditText.isEnabled = e
        binding.phoneEditText.isEnabled = e

        val bg = if (e) R.color.cw_white else R.color.cw_grey_extra_light
        binding.nameEditTextLayout.setBoxBackgroundColorResource(bg)
        binding.emailEditTextLayout.setBoxBackgroundColorResource(bg)
        binding.phoneEditTextLayout.setBoxBackgroundColorResource(bg)
    }
}
