package edu.gatech.cc.cellwatch.ui.main

import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.ui.main.OnboardingActivity.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingScreenTest {
    @Test
    fun fccChallengeModeGoesThroughTheFccInfoScreen() {
        val order = generateSequence(Screen.WELCOME) { it.next(CollectionMode.FCC_CHALLENGE) }.toList()
        assertEquals(
            listOf(Screen.WELCOME, Screen.DATA_USE, Screen.COLLECTION_MODE, Screen.FCC_INFO, Screen.PERMISSIONS),
            order,
        )
    }

    @Test
    fun testingModeSkipsTheFccInfoScreen() {
        assertEquals(Screen.PERMISSIONS, Screen.COLLECTION_MODE.next(CollectionMode.TESTING))
    }

    @Test
    fun screensWithoutANextScreenReturnNullInsteadOfThrowing() {
        for (mode in CollectionMode.values()) {
            assertNull(Screen.READ_MORE.next(mode))
            assertNull(Screen.PERMISSIONS.next(mode))
        }
    }
}
