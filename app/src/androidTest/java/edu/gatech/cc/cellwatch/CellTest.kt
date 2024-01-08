package edu.gatech.cc.cellwatch

import edu.gatech.cc.cellwatch.core.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CellTest {
    private lateinit var telephonyInfoManager: TelephonyInfoManager

    companion object {
        private const val TAG = "CellTest"
    }

    @Before
    fun createTelephonyInfoManager() {
        telephonyInfoManager = TelephonyInfoManager
    }

    @Test
    @Throws(Exception::class)
    fun getMobileCountryCode() {
        Log.d(TAG, "Starting getMobileCountryCode test ********")
        val simMCC = TelephonyInfoManager.getSimMobileCountryCode();
        val netMCC = TelephonyInfoManager.getNetworkMobileCountryCode();
        val simMNC = TelephonyInfoManager.getSimMobileNetworkCode();
        val netMNC = TelephonyInfoManager.getNetworkMobileNetworkCode();

        Log.d(TAG, "sim_mcc = ${simMCC}");
        Log.d(TAG, "net_mcc = ${netMCC}");
        Log.d(TAG, "sim_mnc = ${simMNC}");
        Log.d(TAG, "net_mnc = ${netMNC}");
    }

    @Test
    @Throws(Exception::class)
    fun getCellInfo() {
        Log.d(TAG, "Starting getCellInfo test *******")
        val cells = telephonyInfoManager.getCells()

        cells?.forEach { cell ->
            Log.d(TAG, "${cell.toString()}")
        }
    }
}
