package edu.gatech.cc.cellwatch.domain.telephony.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderNameTest {
    @Test
    fun simCarrierIsPreferredOverTheNetworkItIsCampedOn() {
        assertEquals("t-mobile", providerName(simOperatorName = "T-Mobile", networkOperatorName = "AT&T", roaming = true))
        assertEquals("t-mobile", providerName(simOperatorName = "T-Mobile", networkOperatorName = "AT&T", roaming = false))
    }

    @Test
    fun networkNameIsUsedWhenTheSimHasNoNameAndThePhoneIsHome() {
        assertEquals("verizon", providerName(simOperatorName = "", networkOperatorName = " Verizon ", roaming = false))
        assertEquals("verizon", providerName(simOperatorName = null, networkOperatorName = "Verizon", roaming = false))
    }

    @Test
    fun hostNetworkNameIsNotUsedWhileRoaming() {
        assertEquals("", providerName(simOperatorName = "  ", networkOperatorName = "AT&T", roaming = true))
    }

    @Test
    fun blankWhenNeitherNameIsKnownInsteadOfAPlaceholder() {
        assertEquals("", providerName(simOperatorName = "", networkOperatorName = "", roaming = false))
        assertEquals("", providerName(simOperatorName = null, networkOperatorName = null, roaming = false))
    }

    @Test
    fun namesAreTrimmedAndLowercasedAsBefore() {
        assertEquals("at&t", providerName(simOperatorName = "  AT&T\n", networkOperatorName = "", roaming = false))
    }
}
