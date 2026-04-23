package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.repo.CellRepository
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.LatencyDataRepository
import edu.gatech.cc.cellwatch.domain.repo.LocationRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import edu.gatech.cc.cellwatch.domain.repo.UploadDownloadDataRepository

/**
 * Persists orchestrated sequence artifacts to repositories so the sync layer can upload them.
 */
class RepositoryBackedMeasurementResultStore(
    private val measurementRepository: MeasurementRepository,
    private val latencyDataRepository: LatencyDataRepository,
    private val uploadDownloadDataRepository: UploadDownloadDataRepository,
    private val submissionRepository: FccSubmissionRepository,
    private val locationRepository: LocationRepository? = null,
    private val cellRepository: CellRepository? = null,
) : MeasurementResultStore {

    override suspend fun insertMeasurement(measurement: Measurement) {
        measurementRepository.upsert(measurement)
        measurement.latencyData?.let { latencyDataRepository.upsert(it) }
        measurement.uploadDownloadData?.let { uploadDownloadDataRepository.upsert(it) }
        measurement.locations.orEmpty().forEach { location ->
            locationRepository?.upsert(
                if (location.measurementId == measurement.id) {
                    location
                } else {
                    location.copy(measurementId = measurement.id)
                },
            )
        }
        measurement.cells.orEmpty().forEach { cell ->
            cellRepository?.upsert(
                if (cell.measurementId == measurement.id) {
                    cell
                } else {
                    cell.copy(measurementId = measurement.id)
                },
            )
        }
    }

    override suspend fun insertFccSubmission(submission: FccSubmission) {
        submissionRepository.upsert(submission)
    }
}
