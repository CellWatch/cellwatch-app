package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class UploadTriggerParityArtifact(
    val schemaVersion: Int = 1,
    val platform: String,
    val suite: String = "upload-trigger-default",
    val measurementsUploaded: Int,
    val measurementsMarkedUploaded: Int,
    val submissionsUploaded: Int,
    val submissionsBlockedBeforeUpload: Boolean,
    val uploadTimeEpochMs: Long?,
)

class UploadTriggerParityArtifactFactory {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun build(platform: String, result: UploadTriggerParityResult): UploadTriggerParityArtifact {
        return UploadTriggerParityArtifact(
            platform = platform.lowercase(),
            measurementsUploaded = result.measurementsUploaded,
            measurementsMarkedUploaded = result.measurementsMarkedUploaded,
            submissionsUploaded = result.submissionsUploaded,
            submissionsBlockedBeforeUpload = result.submissionsBlockedBeforeUpload,
            uploadTimeEpochMs = result.uploadTimeEpochMs,
        )
    }

    fun buildJson(platform: String, result: UploadTriggerParityResult): String {
        return json.encodeToString(build(platform, result))
    }

    fun parseJson(jsonText: String): UploadTriggerParityArtifact {
        return json.decodeFromString(jsonText)
    }
}

object UploadTriggerParityArtifactChecker {
    fun keyFieldMismatches(
        left: UploadTriggerParityArtifact,
        right: UploadTriggerParityArtifact,
    ): List<String> {
        val mismatches = mutableListOf<String>()
        if (left.schemaVersion != right.schemaVersion) mismatches += "schemaVersion"
        if (left.suite != right.suite) mismatches += "suite"
        if (left.measurementsUploaded != right.measurementsUploaded) mismatches += "measurementsUploaded"
        if (left.measurementsMarkedUploaded != right.measurementsMarkedUploaded) mismatches += "measurementsMarkedUploaded"
        if (left.submissionsUploaded != right.submissionsUploaded) mismatches += "submissionsUploaded"
        if (left.submissionsBlockedBeforeUpload != right.submissionsBlockedBeforeUpload) mismatches += "submissionsBlockedBeforeUpload"
        if (left.uploadTimeEpochMs != right.uploadTimeEpochMs) mismatches += "uploadTimeEpochMs"
        return mismatches
    }
}
