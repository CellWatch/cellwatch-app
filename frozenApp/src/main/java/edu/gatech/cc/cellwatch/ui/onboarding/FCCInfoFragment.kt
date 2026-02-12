package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.ContactInfoValidator
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.databinding.FragmentFccInformationBinding
import kotlinx.coroutines.launch

class FCCInfoFragment : Fragment() {
    private lateinit var binding: FragmentFccInformationBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFccInformationBinding.inflate(inflater, container, false)

        // only load saved info when this is really a newly created activity
        if (savedInstanceState == null) {
            lifecycleScope.launch { loadSavedFccInfo() }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // enable clicking on link to show privacy policy
        binding.readPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
    }

    private suspend fun loadSavedFccInfo() {
        binding.etName.setText(CellWatchApp.settingsRepository.getName())
        binding.etPhone.setText(CellWatchApp.settingsRepository.getPhoneNumber())
        binding.etEmail.setText(CellWatchApp.settingsRepository.getEmail())
        binding.cbAcknowledgement.isChecked = CellWatchApp.settingsRepository.getFccSharingAcknowledged()
    }

    suspend fun storeData() {
        CellWatchApp.settingsRepository.run {
            setName(binding.etName.text.toString())
            setPhoneNumber(binding.etPhone.text.toString())
            setEmail(binding.etEmail.text.toString())
            setFccSharingAcknowledged(binding.cbAcknowledgement.isChecked)
            setCollectionMode(CollectionMode.FCC_CHALLENGE)
        }
    }

    fun validateInputs(): Boolean {
        val name = binding.etName.text.toString()
        val phone = binding.etPhone.text.toString()
        val email = binding.etEmail.text.toString()
        var valid = true

        if (name.isBlank()) {
            binding.etName.error = getString(R.string.blank_name)
            valid = false
        }

        val validatedPhone = ContactInfoValidator.asValidPhoneNumber(phone)
        if (validatedPhone != null) {
            binding.etPhone.setText(validatedPhone)
        } else {
            binding.etPhone.error = getString(R.string.invalid_phone)
            valid = false
        }

        val validatedEmail = ContactInfoValidator.asValidEmail(email)
        if (validatedEmail != null) {
            binding.etEmail.setText(validatedEmail)
        } else {
            binding.etEmail.error = getString(R.string.invalid_email)
            valid = false
        }

        if (!binding.cbAcknowledgement.isChecked) {
            Toast.makeText(context, R.string.missing_acknowledgement, Toast.LENGTH_LONG).show()
            valid = false
        }

        return valid
    }
}
