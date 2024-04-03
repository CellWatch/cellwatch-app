package edu.gatech.cc.cellwatch.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private val TAG = this::class.simpleName
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var shareArray: Array<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)

        shareArray = arrayOf(getString(R.string.fcc_challenge_mode), getString(R.string.testing_mode))
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, shareArray)
        (binding.collectionModeMenu.editText as? AutoCompleteTextView)?.setAdapter(adapter)


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

        binding.appVersion.text = BuildConfig.VERSION_NAME
        binding.deviceId.text = ""
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.deviceId.text = CellWatchApp.settingsRepository.getDeviceId()
            } catch (t: Throwable) {
                Log.e(TAG, "failed to set device ID")
            }
        }

        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE).let {
            if (it is ClipboardManager) {
                it
            } else {
                Log.e(TAG, "expected ClipboardManager, got $it")
                null
            }
        }

        if (clipboard != null) {
            fun copy(label: String, text: CharSequence) {
                clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                Toast.makeText(requireContext(), getString(R.string.copied, text), Toast.LENGTH_SHORT).show()
            }

            binding.deviceIdRow.setOnClickListener { copy("device ID", binding.deviceId.text) }
            binding.appVersionRow.setOnClickListener {copy("app version", binding.appVersion.text) }
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
