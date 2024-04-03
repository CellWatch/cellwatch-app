package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.CellWatchApp
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
        binding.LLReadPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyText()
        }
    }

    private suspend fun loadSavedFccInfo() {
        binding.etName.setText(CellWatchApp.settingsRepository.getName())
        binding.etPhone.setText(CellWatchApp.settingsRepository.getPhoneNumber())
        binding.etEmail.setText(CellWatchApp.settingsRepository.getEmail())
        binding.cbAcknowledgement.isChecked = CellWatchApp.settingsRepository.getFccSharingAcknowledged()
    }

    private fun showPrivacyPolicyText() {
        //TODO Display Privacy Policy

        //Placeholder toast
        Toast.makeText(
            context,
            "Placeholder text.",
            Toast.LENGTH_LONG
        ).show()
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
            binding.etName.error = "Invalid name"
            valid = false
        }

        if (phone.isBlank() || !Patterns.PHONE.matcher(phone).matches()) {
            binding.etPhone.error = "Invalid phone number"
            valid = false
        }

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Invalid email address"
            valid = false
        }

        if (!binding.cbAcknowledgement.isChecked) {
            Toast.makeText(context, "Please acknowledge the bottom statement.", Toast.LENGTH_LONG).show()
            valid = false
        }

        return valid
    }
}
