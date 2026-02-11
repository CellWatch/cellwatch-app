package edu.gatech.cc.cellwatch.db

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object FccSubmissionQueriesContract {

    fun assertInsertAndSelectById(db: CellwatchDatabase) {
        val id = "fcc-1"
        val now = 1_710_000_000_000L

        db.fccSubmissionQueries.insertOrReplaceFccSubmission(
            id = id,
            challengeDataId = "challenge-1",
            contactName = "Alice",
            contactEmail = "alice@example.com",
            contactPhone = "555",
            deviceTimestamp = now,
            serverTimestamp = now,
            sourceIp = "1.2.3.4",
            sourcePort = 443L,
            deviceId = "device-1",
            deviceImei = "imei",
            deviceTac = "tac",
            deviceType = "phone",
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            deviceOsName = "Android",
            appName = "CellWatch",
            appVersion = "1.0.0",
            provider = "carrier",
            simCountryCode = "310",
            simNetworkCode = "260",
            netCountryCode = "310",
            netNetworkCode = "260",
            inVehicle = 1L,
            externalAntenna = 0L,
            submitted = 0L,
            submittedOn = null,
            submission = null,
            submissionResponse = null,
            createdOn = now,
            updatedOn = now,
            uploadTime = null,
        )

        val row = db.fccSubmissionQueries.selectFccSubmissionById(id).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("Alice", row.contactName)
        assertEquals(0L, row.submitted)
    }

    fun assertSelectUnsubmittedFiltersSubmittedRows(db: CellwatchDatabase) {
        fun insert(id: String, submitted: Long?) {
            db.fccSubmissionQueries.insertOrReplaceFccSubmission(
                id = id,
                challengeDataId = null,
                contactName = null,
                contactEmail = null,
                contactPhone = null,
                deviceTimestamp = null,
                serverTimestamp = null,
                sourceIp = null,
                sourcePort = null,
                deviceId = null,
                deviceImei = null,
                deviceTac = null,
                deviceType = null,
                deviceManufacturer = null,
                deviceModel = null,
                deviceOsName = null,
                appName = null,
                appVersion = null,
                provider = null,
                simCountryCode = null,
                simNetworkCode = null,
                netCountryCode = null,
                netNetworkCode = null,
                inVehicle = null,
                externalAntenna = null,
                submitted = submitted,
                submittedOn = null,
                submission = null,
                submissionResponse = null,
                createdOn = 1L,
                updatedOn = 1L,
                uploadTime = null,
            )
        }

        insert("unsubmitted-null", null)
        insert("unsubmitted-zero", 0L)
        insert("submitted-one", 1L)

        val rows = db.fccSubmissionQueries.selectUnsubmittedFccSubmissions().executeAsList()
        assertEquals(listOf("unsubmitted-null", "unsubmitted-zero"), rows.map { it.id })
    }

    fun assertDeleteByIdRemovesRow(db: CellwatchDatabase) {
        val id = "fcc-delete"
        db.fccSubmissionQueries.insertOrReplaceFccSubmission(
            id = id,
            challengeDataId = null,
            contactName = null,
            contactEmail = null,
            contactPhone = null,
            deviceTimestamp = null,
            serverTimestamp = null,
            sourceIp = null,
            sourcePort = null,
            deviceId = null,
            deviceImei = null,
            deviceTac = null,
            deviceType = null,
            deviceManufacturer = null,
            deviceModel = null,
            deviceOsName = null,
            appName = null,
            appVersion = null,
            provider = null,
            simCountryCode = null,
            simNetworkCode = null,
            netCountryCode = null,
            netNetworkCode = null,
            inVehicle = null,
            externalAntenna = null,
            submitted = null,
            submittedOn = null,
            submission = null,
            submissionResponse = null,
            createdOn = null,
            updatedOn = null,
            uploadTime = null,
        )
        assertNotNull(db.fccSubmissionQueries.selectFccSubmissionById(id).executeAsOneOrNull())
        db.fccSubmissionQueries.deleteFccSubmissionById(id)
        assertNull(db.fccSubmissionQueries.selectFccSubmissionById(id).executeAsOneOrNull())
    }
}
