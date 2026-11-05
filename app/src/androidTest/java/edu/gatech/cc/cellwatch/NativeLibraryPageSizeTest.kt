package edu.gatech.cc.cellwatch

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLibraryPageSizeTest {
    @Test
    fun every64BitLibraryButConscryptsSupports16KbPages() {
        // Conscrypt is updated on its own, since the speed test's TLS goes through it.
        val libraries = NativeLibraries.inInstalledApp().filter { it.name != "libconscrypt_jni.so" }

        assertTrue("found $libraries", libraries.map { it.name }.containsAll(listOf("libmapbox-maps.so", "libh3-java.so")))
        assertEquals(emptyList<NativeLibraries.Library>(), libraries.filterNot { it.supports16KbPages })
    }
}
