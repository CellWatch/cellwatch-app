package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class SyncReport(
    val attempted: Int = 0,
    val uploaded: Int = 0,
    val markedUploaded: Int = 0,
    val networkErrors: Int = 0,
    val unexpectedErrors: Int = 0,
    val blockedBeforeUpload: Boolean = false,
    val errorSummary: SyncErrorSummary = SyncErrorSummary(),
)

class MeasurementSyncUseCase(
    private val localStore: MeasurementSyncLocalStore,
    private val remoteDataSource: MeasurementSyncRemoteDataSource,
    private val tcpTupleProvider: TcpTupleProvider,
    private val clock: Clock = Clock.System,
) {

    suspend fun syncMeasurements(): SyncReport {
        val measurements = localStore.getUnsyncedMeasurements()
        if (measurements.isEmpty()) return SyncReport()

        var report = SyncReport(attempted = measurements.size)
        for (measurement in measurements) {
            report = try {
                remoteDataSource.insertMeasurement(measurement)
                localStore.markMeasurementUploaded(measurement.id, clock.now())
                report.copy(uploaded = report.uploaded + 1)
            } catch (e: NetworkError) {
                report.copy(
                    networkErrors = report.networkErrors + 1,
                    errorSummary = report.errorSummary.recordError(
                        category = SyncErrorCategory.NETWORK,
                        throwable = e,
                        contextId = measurement.id,
                    ),
                )
            } catch (e: DuplicateKeyError) {
                if (canMarkUploaded(measurement)) {
                    localStore.markMeasurementUploaded(measurement.id, clock.now())
                    report.copy(markedUploaded = report.markedUploaded + 1)
                } else {
                    report.copy(
                        unexpectedErrors = report.unexpectedErrors + 1,
                        errorSummary = report.errorSummary.recordError(
                            category = SyncErrorCategory.DUPLICATE_NOT_MARKED,
                            throwable = e,
                            contextId = measurement.id,
                        ),
                    )
                }
            } catch (e: Exception) {
                report.copy(
                    unexpectedErrors = report.unexpectedErrors + 1,
                    errorSummary = report.errorSummary.recordError(
                        category = SyncErrorCategory.UNEXPECTED,
                        throwable = e,
                        contextId = measurement.id,
                    ),
                )
            }
        }

        return report
    }

    suspend fun syncFccSubmissions(): SyncReport {
        val submissions = localStore.getUnsyncedSubmissions()
        if (submissions.isEmpty()) return SyncReport()

        val tuple = try {
            tcpTupleProvider.getPublicTcpTuple()
        } catch (e: Exception) {
            return SyncReport(
                attempted = submissions.size,
                blockedBeforeUpload = true,
                unexpectedErrors = submissions.size,
                errorSummary = SyncErrorSummary().recordError(
                    category = SyncErrorCategory.BLOCKED,
                    throwable = e,
                    contextId = "tuple-provider",
                ),
            )
        }

        var report = SyncReport(attempted = submissions.size)
        for (submission in submissions) {
            val patched = patchWithTuple(submission, tuple.remoteAddress, tuple.remotePort, tuple.timestamp)
            report = try {
                remoteDataSource.insertFccSubmission(patched)
                localStore.markSubmissionUploaded(submission.id, clock.now())
                report.copy(uploaded = report.uploaded + 1)
            } catch (e: NetworkError) {
                report.copy(
                    networkErrors = report.networkErrors + 1,
                    errorSummary = report.errorSummary.recordError(
                        category = SyncErrorCategory.NETWORK,
                        throwable = e,
                        contextId = submission.id,
                    ),
                )
            } catch (e: Exception) {
                report.copy(
                    unexpectedErrors = report.unexpectedErrors + 1,
                    errorSummary = report.errorSummary.recordError(
                        category = SyncErrorCategory.UNEXPECTED,
                        throwable = e,
                        contextId = submission.id,
                    ),
                )
            }
        }

        return report
    }

    suspend fun canMarkUploaded(measurement: Measurement): Boolean {
        return try {
            val remoteMeasurement = remoteDataSource.getMeasurementById(measurement.id)
            remoteMeasurement.timestamp == measurement.timestamp
        } catch (_: NotFoundError) {
            false
        } catch (_: NetworkError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun patchWithTuple(
        submission: FccSubmission,
        ip: String,
        port: Int,
        serverEpochMillis: Long,
    ): FccSubmission = submission.copy(
        sourceIp = ip,
        sourcePort = port,
        serverTimestamp = Instant.fromEpochMilliseconds(serverEpochMillis),
    )
}
