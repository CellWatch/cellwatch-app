package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.core.util.ContactInfoValidator
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement

enum class FccSubmissionValidationCode {
    MISSING_LATENCY_MEASUREMENT,
    MISSING_DOWNLOAD_MEASUREMENT,
    MISSING_UPLOAD_MEASUREMENT,
    MISSING_APP_NAME,
    MISSING_APP_VERSION,
    MISSING_DEVICE_ID,
    MISSING_DEVICE_TYPE,
    MISSING_DEVICE_MANUFACTURER,
    MISSING_DEVICE_MODEL,
    MISSING_DEVICE_OS_NAME,
    MISSING_PROVIDER_NAME,
    MISSING_CONTACT_NAME,
    MISSING_CONTACT_EMAIL,
    INVALID_CONTACT_EMAIL,
    MISSING_CONTACT_PHONE,
    INVALID_CONTACT_PHONE,
}

data class FccSubmissionValidationResult(
    val allowed: Boolean,
    val codes: Set<FccSubmissionValidationCode>,
) {
    companion object {
        val Allowed = FccSubmissionValidationResult(
            allowed = true,
            codes = emptySet(),
        )
    }
}

object FccSubmissionValidationPolicy {
    fun validate(
        submission: FccSubmission,
        latencyMeasurement: Measurement?,
        downloadMeasurement: Measurement?,
        uploadMeasurement: Measurement?,
    ): FccSubmissionValidationResult {
        val failures = linkedSetOf<FccSubmissionValidationCode>()

        if (latencyMeasurement == null || latencyMeasurement.type != "latency" || latencyMeasurement.latencyData == null) {
            failures += FccSubmissionValidationCode.MISSING_LATENCY_MEASUREMENT
        }
        if (downloadMeasurement == null || downloadMeasurement.type != "download" || downloadMeasurement.uploadDownloadData == null) {
            failures += FccSubmissionValidationCode.MISSING_DOWNLOAD_MEASUREMENT
        }
        if (uploadMeasurement == null || uploadMeasurement.type != "upload" || uploadMeasurement.uploadDownloadData == null) {
            failures += FccSubmissionValidationCode.MISSING_UPLOAD_MEASUREMENT
        }

        if (submission.appName.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_APP_NAME
        if (submission.appVersion.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_APP_VERSION
        if (submission.deviceId.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_DEVICE_ID
        if (submission.deviceType.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_DEVICE_TYPE
        if (submission.deviceManufacturer.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_DEVICE_MANUFACTURER
        if (submission.deviceModel.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_DEVICE_MODEL
        if (submission.deviceOsName.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_DEVICE_OS_NAME
        if (!isIosSubmission(submission) && submission.provider.isNullOrBlank()) {
            failures += FccSubmissionValidationCode.MISSING_PROVIDER_NAME
        }
        if (submission.contactName.isNullOrBlank()) failures += FccSubmissionValidationCode.MISSING_CONTACT_NAME

        val email = submission.contactEmail?.trim().orEmpty()
        if (email.isBlank()) {
            failures += FccSubmissionValidationCode.MISSING_CONTACT_EMAIL
        } else if (ContactInfoValidator.asValidEmail(email) == null) {
            failures += FccSubmissionValidationCode.INVALID_CONTACT_EMAIL
        }

        val phone = submission.contactPhone?.trim().orEmpty()
        if (phone.isBlank()) {
            failures += FccSubmissionValidationCode.MISSING_CONTACT_PHONE
        } else if (ContactInfoValidator.asValidPhoneNumber(phone) == null) {
            failures += FccSubmissionValidationCode.INVALID_CONTACT_PHONE
        }

        return if (failures.isEmpty()) {
            FccSubmissionValidationResult.Allowed
        } else {
            FccSubmissionValidationResult(
                allowed = false,
                codes = failures,
            )
        }
    }

    private fun isIosSubmission(submission: FccSubmission): Boolean {
        val deviceType = submission.deviceType?.trim()?.lowercase()
        val deviceOsName = submission.deviceOsName?.trim()?.lowercase()
        return deviceType == "ios" ||
            deviceOsName?.contains("ios") == true ||
            deviceOsName?.contains("iphone") == true
    }
}
