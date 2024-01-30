package com.cellwatch.ui.onboarding

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cellwatch.R


class FCCInfoFragment : Fragment() {
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_fcc_information, container, false)
        etName = view.findViewById(R.id.etName)
        etPhone = view.findViewById(R.id.etPhone)
        etEmail = view.findViewById(R.id.etEmail)
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
    }

    private fun validateInputs(): Boolean {
        //TODO Needed?
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
        return true
    }
}
