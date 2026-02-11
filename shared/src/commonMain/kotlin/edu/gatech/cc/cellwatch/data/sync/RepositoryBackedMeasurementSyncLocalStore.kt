package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.repo.CellRepository
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.LatencyDataRepository
import edu.gatech.cc.cellwatch.domain.repo.LocationRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import edu.gatech.cc.cellwatch.domain.repo.UploadDownloadDataRepository
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncLocalStore
import kotlinx.datetime.Instant

class RepositoryBackedMeasurementSyncLocalStore(
    private val measurementRepository: MeasurementRepository,
    private val uploadDownloadDataRepository: UploadDownloadDataRepository,
    private val latencyDataRepository: LatencyDataRepository,
    private val locationRepository: LocationRepository,
    private val cellRepository: CellRepository,
    private val fccSubmissionRepository: FccSubmissionRepository,
) : MeasurementSyncLocalStore {

    override suspend fun getUnsyncedMeasurements(): List<Measurement> =
        measurementRepository.getUnsynced().map { measurement ->
            measurement.copy(
                uploadDownloadData = uploadDownloadDataRepository
                    .getByMeasurementId(measurement.id)
                    .firstOrNull(),
                latencyData = latencyDataRepository
                    .getByMeasurementId(measurement.id)
                    .firstOrNull(),
                locations = locationRepository.getByMeasurementId(measurement.id),
                cells = cellRepository.getByMeasurement(measurement.id),
            )
        }

    override suspend fun markMeasurementUploaded(id: String, uploadedAt: Instant) {
        measurementRepository.markUploaded(id, uploadedAt)
    }

    override suspend fun getUnsyncedSubmissions(): List<FccSubmission> =
        fccSubmissionRepository.getUnsynced()

    override suspend fun markSubmissionUploaded(id: String, uploadedAt: Instant) {
        fccSubmissionRepository.markUploaded(id, uploadedAt)
    }
}
