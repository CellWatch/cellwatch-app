package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UploadTriggerParityArtifactTest {

    @Test
    fun schemaAndKeyFieldParity_androidVsIosArtifacts_match() = runBlocking {
        val result = UploadTriggerParityHarness().runDefaultScenario()
        val factory = UploadTriggerParityArtifactFactory()

        val androidJson = factory.buildJson(platform = "android", result = result)
        val iosJson = factory.buildJson(platform = "ios", result = result)

        val androidArtifact = factory.parseJson(androidJson)
        val iosArtifact = factory.parseJson(iosJson)

        assertEquals(1, androidArtifact.schemaVersion)
        assertEquals(1, iosArtifact.schemaVersion)
        assertEquals("android", androidArtifact.platform)
        assertEquals("ios", iosArtifact.platform)

        val mismatches = UploadTriggerParityArtifactChecker.keyFieldMismatches(
            left = androidArtifact,
            right = iosArtifact,
        )
        assertTrue(mismatches.isEmpty(), "Expected key parity fields to match but mismatched: $mismatches")
    }
}
