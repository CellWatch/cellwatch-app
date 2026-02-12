package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerParityHarness
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerParityArtifactFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadTriggerParityHarnessAndroidTest {

    @Test
    fun defaultScenario_matchesExpectedParityContract() = runBlocking {
        val result = UploadTriggerParityHarness().runDefaultScenario()
        val artifactFactory = UploadTriggerParityArtifactFactory()
        val artifactJson = artifactFactory.buildJson(platform = "android", result = result)
        val parsedArtifact = artifactFactory.parseJson(artifactJson)

        println("uploadTriggerParityArtifact=$artifactJson")

        assertEquals(1, result.measurementsUploaded)
        assertEquals(1, result.measurementsMarkedUploaded)
        assertEquals(1, result.submissionsUploaded)
        assertTrue(result.submissionsBlockedBeforeUpload)
        assertEquals(1_710_000_009_000L, result.uploadTimeEpochMs)
        assertEquals(1, parsedArtifact.schemaVersion)
        assertEquals("android", parsedArtifact.platform)
    }
}
