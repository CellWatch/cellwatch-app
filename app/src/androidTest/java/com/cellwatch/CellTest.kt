package com.cellwatch

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellwatch.domain.telephony.managers.TelephonyInfoManager
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
    fun getCellInfo() {
        Log.d(TAG, "Starting getCellInfo test *******")
        val cells = telephonyInfoManager.getCells()

        cells?.forEach { cell ->
            Log.d(TAG, "${cell.toString()}")
        }
    }
}