package edu.gatech.cc.cellwatch.ui.onboarding

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.textfield.TextInputEditText
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.ui.onboarding.viewmodels.OnboardingViewModel
import edu.gatech.cc.cellwatch.ui.onboarding.viewmodels.OnboardingViewModelFactory

class FCCInfoFragment : Fragment() {
    private val TAG = this::class.simpleName

    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var awkCheckbox: CheckBox

    private val viewModel: OnboardingViewModel by activityViewModels<OnboardingViewModel> {
        OnboardingViewModelFactory(CellWatchApp.localDataStore)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_fcc_information, container, false)
        etName = view.findViewById(R.id.etName)
        etPhone = view.findViewById(R.id.etPhone)
        etEmail = view.findViewById(R.id.etEmail)
        awkCheckbox = view.findViewById(R.id.cbAcknowledgement)

        observeFccInfo()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the TextView and make it clickable
        val tvReadPrivacyPolicy = view.findViewById<LinearLayout>(R.id.LLReadPrivacyPolicy)
        tvReadPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyText()
        }
    }

    private fun observeFccInfo() {
        viewModel.getUserName().observe(this) { userName ->
            Log.d(TAG, "userName = $userName")
            etName.setText(userName)
        }

        viewModel.getPhoneNumber().observe(this) { phoneNumber ->
            Log.d(TAG, "phoneNumber = $phoneNumber")
            etPhone.setText(phoneNumber)
        }

        viewModel.getEmail().observe(this) { email ->
            Log.d(TAG, "email = $email")
            etEmail.setText(email)
        }

        viewModel.getFccPolicyAgreed().observe(this) { fccPolicyAgreed ->
            Log.d(TAG, "fccPolicyAgreed = $fccPolicyAgreed")
            awkCheckbox.isChecked = fccPolicyAgreed
        }
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

    private fun storeData() {
        //TODO Do something with name, email, phone
        val name = etName.text.toString()
        val phone = etPhone.text.toString()
        val email = etEmail.text.toString()
        val fccPolicyAgreed = awkCheckbox.isChecked

        viewModel.setUserName(name)
        viewModel.setPhoneNumber(phone)
        viewModel.setEmail(email)
        viewModel.agreeToFccPolicy(fccPolicyAgreed)
    }

    fun validateInputs(): Boolean {
        val name = etName.text.toString()
        val phone = etPhone.text.toString()
        val email = etEmail.text.toString()

        if (name.isEmpty() || !name.matches("[a-zA-Z\\s'-]+".toRegex())) {
            etName.error = "Invalid name"
            return false
        }

        if (phone.isEmpty() || !Patterns.PHONE.matcher(phone).matches()) {
            etPhone.error = "Invalid phone number"
            return false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Invalid email address"
            return false
        }

        if (!awkCheckbox.isChecked) {
            Toast.makeText(context, "Please acknowledge the bottom statement.", Toast.LENGTH_LONG).show()
            return false
        }

        storeData()

        return true
    }
}
