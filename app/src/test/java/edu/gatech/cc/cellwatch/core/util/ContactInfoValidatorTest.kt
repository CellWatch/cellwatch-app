package edu.gatech.cc.cellwatch.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The contact details onboarding and Settings accept, checked against §5.1.1 of the FCC's mobile
 * speed test data spec (July 28, 2025): a US phone number as 000-000-0000 and a valid email address.
 */
class ContactInfoValidatorTest {
    @Test
    fun phoneInTheSpecsFormatIsKept() {
        assertEquals("888-225-5322", ContactInfoValidator.asValidPhoneNumber("888-225-5322"))
    }

    @Test
    fun tenDigitsArePutInTheSpecsFormat() {
        assertEquals("888-225-5322", ContactInfoValidator.asValidPhoneNumber("8882255322"))
    }

    @Test
    fun otherPhoneFormatsAreRefused() {
        val refused = listOf(
            "",
            "888-225-532",
            "888-225-53222",
            "88822553222",
            "(888) 225-5322",
            "888.225.5322",
            "888 225 5322",
            "1-888-225-5322",
            "+1 888-225-5322",
            "888-225-532a",
        )
        for (phone in refused) {
            assertNull(phone, ContactInfoValidator.asValidPhoneNumber(phone))
        }
    }

    @Test
    fun validEmailIsKept() {
        // §5.1.1's example.
        assertEquals("jane.broadband@fcc.gov", ContactInfoValidator.asValidEmail("jane.broadband@fcc.gov"))
    }

    @Test
    fun malformedEmailIsRefused() {
        val refused = listOf("", " ", "jane.broadband", "jane.broadband@", "@fcc.gov", "jane broadband@fcc.gov", "jane.broadband@fcc")
        for (email in refused) {
            assertNull(email, ContactInfoValidator.asValidEmail(email))
        }
    }
}
