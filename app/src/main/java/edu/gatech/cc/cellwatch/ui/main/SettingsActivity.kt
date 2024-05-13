package edu.gatech.cc.cellwatch.ui.main

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.setCopyOnClick
import edu.gatech.cc.cellwatch.data.core.repositories.SettingsRepository
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch
import org.apache.commons.validator.routines.EmailValidator

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var collectionModeToString: Map<CollectionMode, String>
    private lateinit var stringToCollectionMode: Map<String, CollectionMode>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navDrawer.setOnCloseListener { binding.root.closeDrawer(GravityCompat.START) }
        binding.navDrawer.setActiveActivity(this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {binding.root.openDrawer(GravityCompat.START) }

        collectionModeToString = mapOf(
            CollectionMode.FCC_CHALLENGE to getString(R.string.fcc_challenge_mode),
            CollectionMode.TESTING to getString(R.string.testing_mode)
        )
        stringToCollectionMode = collectionModeToString.entries.associate { it.value to it.key }

        val adapter = ArrayAdapter(this, R.layout.dropdown_item, collectionModeToString.values.toTypedArray())
        binding.collectionModeTextView.setAdapter(adapter)
        binding.collectionModeTextView.setOnItemClickListener { _, _, _, _ ->
            val text = binding.collectionModeTextView.text.toString()
            val mode = stringToCollectionMode[text] ?: throw RuntimeException("invalid mode selected: $text")
            handleCollectionModeChanged(mode)
        }

        binding.settingsEditButton.setOnClickListener { setEditable(true) }
        binding.submitEdit.setOnClickListener { handleApplyContactInfoChanges() }

        binding.appVersion.text = BuildConfig.VERSION_NAME
        binding.deviceId.text = ""
        lifecycleScope.launch {
            loadSavedFccInfo()
            binding.deviceId.text = CellWatchApp.settingsRepository.getDeviceId()
        }

        binding.deviceIdRow.setCopyOnClick("device ID") { binding.deviceId.text }
        binding.appVersionRow.setCopyOnClick("app version") { binding.appVersion.text }

        // enable clicking on link to show privacy policy
        binding.dataReminder.movementMethod = LinkMovementMethod.getInstance()

        setEditable(false)
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

    private suspend fun loadSavedFccInfo() {
        binding.nameEditText.setText(CellWatchApp.settingsRepository.getName())
        binding.phoneEditText.setText(CellWatchApp.settingsRepository.getPhoneNumber())
        binding.emailEditText.setText(CellWatchApp.settingsRepository.getEmail())

        val collectionMode = CellWatchApp.settingsRepository.getCollectionMode()
        binding.collectionModeTextView.setText(collectionModeToString[collectionMode], false)
    }

    private fun handleApplyContactInfoChanges() {
        if (!validateInputs()) {
            return
        }

        val context = this
        lifecycleScope.launch {
            try {
                CellWatchApp.settingsRepository.run {
                    setName(binding.nameEditText.text.toString())
                    setPhoneNumber(binding.phoneEditText.text.toString())
                    setEmail(binding.emailEditText.text.toString())
                }
                setEditable(false)
            } catch (e: SettingsRepository.BlankInFccChallengeModeException) {
                Toast.makeText(context, getString(R.string.blank_info_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleCollectionModeChanged(mode: CollectionMode) {
        lifecycleScope.launch {
            when (mode) {
                CollectionMode.TESTING -> CellWatchApp.settingsRepository.setCollectionMode(mode)
                CollectionMode.FCC_CHALLENGE -> enableFccChallengeMode(true)
            }
        }
    }

    private suspend fun enableFccChallengeMode(checkAcknowledgement: Boolean) {
        if (checkAcknowledgement && !CellWatchApp.settingsRepository.getFccSharingAcknowledged()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.fcc_acknowledgement)
                .setPositiveButton(R.string.acknowledge) { dialog, _ ->
                    dialog.dismiss()
                    lifecycleScope.launch {
                        CellWatchApp.settingsRepository.setFccSharingAcknowledged(true)
                        enableFccChallengeMode(false)
                    }
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    binding.collectionModeTextView.setText(collectionModeToString[CollectionMode.TESTING], false)
                }
                .show()

            return
        }

        try {
            CellWatchApp.settingsRepository.setCollectionMode(CollectionMode.FCC_CHALLENGE)
        } catch (e: SettingsRepository.MissingFccInfoException) {
            Toast.makeText(this, getString(R.string.missing_fcc_info_error), Toast.LENGTH_LONG).show()
            binding.collectionModeTextView.setText(collectionModeToString[CollectionMode.TESTING], false)
        }
    }

    private fun validateInputs(): Boolean {
        val phone = binding.phoneEditText.text.toString()
        val email = binding.emailEditText.text.toString()
        var valid = true

        if (phone.isNotEmpty()) {
            val phoneUtil = PhoneNumberUtil.getInstance()
            val numberProto = try {
                phoneUtil.parse(phone, "US")
            } catch (e: NumberParseException) {
                null
            }
            if (numberProto == null || !phoneUtil.isValidNumber(numberProto)) {
                binding.phoneEditText.error = "Invalid phone number"
                valid = false
            }
        }

        if (email.isNotEmpty() && !EmailValidator.getInstance().isValid(email)) {
            binding.emailEditText.error = "Invalid email address"
            valid = false
        }

        return valid
    }
}