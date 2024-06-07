package edu.gatech.cc.cellwatch.core.util

import org.apache.commons.validator.routines.EmailValidator

object ContactInfoValidator {
    fun asValidPhoneNumber(phone: String): String? {
        return if (phone.matches(Regex("^[0-9]{3}-[0-9]{3}-[0-9]{4}"))) {
            phone
        } else if (phone.matches(Regex("^[0-9]{10}$"))) {
            "${phone.substring(0, 3)}-${phone.substring(3, 6)}-${phone.substring(6)}"
        } else {
            null
        }
    }

    fun asValidEmail(email: String): String? {
        return if (email.isNotBlank() && EmailValidator.getInstance().isValid(email)) {
            email
        } else {
            null
        }
    }
}