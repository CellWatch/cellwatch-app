package edu.gatech.cc.cellwatch.db

import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.ChallengeDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.ChallengeData
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object Phase1DataFlowContract {

    suspend fun assertMeasurementGroupLifecycle(db: CellwatchDatabase) {
        val challengeRepo = ChallengeDataRepositoryImpl(db.challengeDataQueries, EmptyCoroutineContext)
        val measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        val locationRepo = LocationRepositoryImpl(db.locationQueries, EmptyCoroutineContext)
        val cellRepo = CellRepositoryImpl(db.cellQueries, EmptyCoroutineContext)
        val latencyRepo = LatencyDataRepositoryImpl(db.latencyDataQueries, EmptyCoroutineContext)
        val uploadRepo = UploadDownloadDataRepositoryImpl(db.uploadDownloadDataQueries, EmptyCoroutineContext)
        val submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)

        val baseMs = 1_710_000_000_000L
        val now = Instant.fromEpochMilliseconds(baseMs)
        val plus10s = Instant.fromEpochMilliseconds(baseMs + 10_000)
        val plus20s = Instant.fromEpochMilliseconds(baseMs + 20_000)
        val plus1s = Instant.fromEpochMilliseconds(baseMs + 1_000)
        val groupId = "group-1"
        val challengeId = "challenge-1"
        val latencyId = "measurement-latency"
        val downloadId = "measurement-download"
        val uploadId = "measurement-upload"

        challengeRepo.upsert(
            ChallengeData(
                id = challengeId,
                submissionCategory = "FCC",
                contactName = "Alice",
                contactEmail = "alice@example.com",
                contactPhone = "555-1111",
                dataSharingAcknowledgement = true,
                createdOn = now,
                updatedOn = now,
            )
        )

        measurementRepo.upsert(
            Measurement(
                id = latencyId,
                groupId = groupId,
                type = "latency",
                timestamp = now,
                success = true,
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
                createdOn = now,
                updatedOn = now,
            )
        )
        measurementRepo.upsert(
            Measurement(
                id = downloadId,
                groupId = groupId,
                type = "download",
                timestamp = plus10s,
                success = true,
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
                createdOn = now,
                updatedOn = now,
            )
        )
        measurementRepo.upsert(
            Measurement(
                id = uploadId,
                groupId = groupId,
                type = "upload",
                timestamp = plus20s,
                success = true,
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
                createdOn = now,
                updatedOn = now,
            )
        )

        latencyRepo.upsert(
            LatencyData(
                id = "latency-data-1",
                measurementId = latencyId,
                rtt = 25,
                jitter = 3,
                sent = 30,
                received = 29,
                servers = listOf("latency.example.com"),
                createdOn = now,
                updatedOn = now,
            )
        )
        uploadRepo.upsert(
            UploadDownloadData(
                id = "download-data-1",
                measurementId = downloadId,
                bytes = 10_000_000,
                duration = 2_000_000,
                bytesPerSec = 5_000_000.0,
                servers = listOf("download.example.com"),
                createdOn = now,
                updatedOn = now,
            )
        )
        uploadRepo.upsert(
            UploadDownloadData(
                id = "upload-data-1",
                measurementId = uploadId,
                bytes = 5_000_000,
                duration = 2_000_000,
                bytesPerSec = 2_500_000.0,
                servers = listOf("upload.example.com"),
                createdOn = now,
                updatedOn = now,
            )
        )

        locationRepo.upsert(
            Location(
                id = "loc-1",
                measurementId = latencyId,
                timestamp = now,
                lat = 33.7488,
                lon = -84.3880,
                createdOn = now,
                updatedOn = now,
            )
        )
        locationRepo.upsert(
            Location(
                id = "loc-2",
                measurementId = latencyId,
                timestamp = plus1s,
                lat = 33.7498,
                lon = -84.3870,
                createdOn = now,
                updatedOn = now,
            )
        )

        cellRepo.upsert(
            Cell(
                id = "cell-1",
                measurementId = latencyId,
                timestamp = now,
                cellId = 12345L,
                networkGeneration = "5G",
                networkSubtype = "NR",
                signalStrength = -95,
                physicalCellId = 12,
                cellConnection = 1,
                rssi = -70,
                rsrp = -105,
                rsrq = -10,
                sinr = 18,
                csiRsrp = null,
                csiRsrq = null,
                csiSinr = null,
                cqi = null,
                spectrumBand = "n78",
                spectrumBandwidth = 100f,
                arfcn = 620000,
                createdOn = now,
                updatedOn = now,
            )
        )

        submissionRepo.upsert(
            FccSubmission(
                id = groupId,
                challengeDataId = challengeId,
                contactName = "Alice",
                contactEmail = "alice@example.com",
                contactPhone = "555-1111",
                provider = "carrier",
                submitted = false,
                createdOn = now,
                updatedOn = now,
            )
        )

        val measurements = measurementRepo.getByGroupId(groupId)
        assertEquals(3, measurements.size)

        val byType = measurements.associateBy { it.type }
        val latency = byType["latency"]
        val download = byType["download"]
        val upload = byType["upload"]
        assertNotNull(latency)
        assertNotNull(download)
        assertNotNull(upload)

        val hydratedLatency = latency.copy(
            latencyData = latencyRepo.getByMeasurementId(latency.id).firstOrNull(),
            locations = locationRepo.getByMeasurementId(latency.id),
            cells = cellRepo.getByMeasurement(latency.id),
        )
        val hydratedDownload = download.copy(
            uploadDownloadData = uploadRepo.getByMeasurementId(download.id).firstOrNull(),
        )
        val hydratedUpload = upload.copy(
            uploadDownloadData = uploadRepo.getByMeasurementId(upload.id).firstOrNull(),
        )
        val submission = submissionRepo.getById(groupId)
        assertNotNull(submission)

        val group = MeasurementGroup(
            latency = hydratedLatency,
            download = hydratedDownload,
            upload = hydratedUpload,
            submission = submission,
        )

        assertEquals(groupId, group.id)
        assertEquals(2, hydratedLatency.locations?.size)
        assertEquals(1, hydratedLatency.cells?.size)
        assertNotNull(group.centerLatLon())
        assertEquals(false, submission.submitted)

        measurementRepo.delete(latencyId)
        assertEquals(0, locationRepo.getByMeasurementId(latencyId).size)
        assertEquals(0, latencyRepo.getByMeasurementId(latencyId).size)
        assertEquals(0, cellRepo.getByMeasurement(latencyId).size)
        assertNull(measurementRepo.getById(latencyId))
    }
}
