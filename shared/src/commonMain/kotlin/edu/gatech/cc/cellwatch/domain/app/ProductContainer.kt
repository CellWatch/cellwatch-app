package edu.gatech.cc.cellwatch.domain.app

import edu.gatech.cc.cellwatch.data.remote.DeviceCredentialStorage
import edu.gatech.cc.cellwatch.data.remote.PersistentDeviceAuthStore
import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.sync.DefaultSyncRemoteDataSourceFactory
import edu.gatech.cc.cellwatch.data.sync.MeasurementSyncServiceFactory
import edu.gatech.cc.cellwatch.data.sync.SupabaseSyncRemoteDataSourceProvider
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.fcc.DefaultMsakMeasurementSequenceOrchestratorFactory
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionProfile
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceProgressListener
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeMeasurementLocationSnapshot
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfile
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSequenceSyncOrchestrator
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusPresenter
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusStore
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusSummary
import edu.gatech.cc.cellwatch.domain.sync.tcpTupleProviderFor
import kotlinx.datetime.Clock
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The pieces of the product app only a platform can build.
 *
 * Everything downstream of this - repositories, result store, sync, the
 * measurement sequence - is assembled once in [ProductContainer] and shared.
 * The iOS harnesses each re-assembled that graph inline (roughly 150 lines
 * apiece), which is how the three of them drifted apart; product screens go
 * through the container instead.
 *
 * The database in particular must be **persistent and app-wide**: the harnesses
 * open a fresh `...-${uuid4()}.db` per run because a test wants isolation, but
 * a product app doing that would forget every measurement the moment a run
 * ended, and History would always be empty.
 */
interface ProductPlatformServices {
    val database: CellwatchDatabase
    val capabilityProvider: PlatformCapabilityProvider
    val credentialStorage: DeviceCredentialStorage
    /**
     * App identity and contact details, read fresh: contact details come from
     * the onboarding profile and the user can change them.
     *
     * Deliberately not the whole [FccSubmissionProfile] - the container fills
     * `deviceId` from the persisted credential, so the id a submission carries
     * is the same one sync authenticates with. The legacy Android path minted a
     * fresh UUID whenever `Settings.Secure` had no id, so those two could
     * disagree.
     */
    fun submissionIdentity(): ProductSubmissionIdentity

    /**
     * Carries the MSAK config and the Supabase target together.
     *
     * Non-null by design: without a runtime profile the app cannot measure at
     * all, which is why `MeasurementStartViewModel` refuses to start without
     * one. Taking the whole profile rather than its parts also stops the MSAK
     * endpoint and the Supabase target being resolved separately and
     * disagreeing.
     */
    val runtimeProfile: RuntimeSyncMsakProfile

    val tcpTupleUrl: String?
    val appSource: String

    /** Persists what the last upload attempt did, so status survives a launch. */
    val syncStatusStore: SyncStatusStore
}

/**
 * The parts of an FCC submission profile the platform knows.
 */
data class ProductSubmissionIdentity(
    val appName: String,
    val appVersion: String,
    val provider: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
)

/**
 * Composition root for the product app. One per process; screens receive it.
 */
class ProductContainer(
    private val services: ProductPlatformServices,
    private val clock: Clock = Clock.System,
) {
    val database: CellwatchDatabase get() = services.database

    val measurementRepository by lazy {
        MeasurementRepositoryImpl(queries = database.measurementQueries, io = EmptyCoroutineContext)
    }
    private val latencyRepository by lazy {
        LatencyDataRepositoryImpl(queries = database.latencyDataQueries, io = EmptyCoroutineContext)
    }
    private val uploadDownloadRepository by lazy {
        UploadDownloadDataRepositoryImpl(queries = database.uploadDownloadDataQueries, io = EmptyCoroutineContext)
    }
    val submissionRepository by lazy {
        FccSubmissionRepositoryImpl(queries = database.fccSubmissionQueries, io = EmptyCoroutineContext)
    }
    val locationRepository by lazy {
        LocationRepositoryImpl(queries = database.locationQueries, io = EmptyCoroutineContext)
    }
    private val cellRepository by lazy {
        CellRepositoryImpl(queries = database.cellQueries, io = EmptyCoroutineContext)
    }

    private val deviceAuthStore by lazy { PersistentDeviceAuthStore(services.credentialStorage) }

    private val resultStore by lazy {
        RepositoryBackedMeasurementResultStore(
            measurementRepository = measurementRepository,
            latencyDataRepository = latencyRepository,
            uploadDownloadDataRepository = uploadDownloadRepository,
            submissionRepository = submissionRepository,
            locationRepository = locationRepository,
            cellRepository = cellRepository,
        )
    }

    private val uploadTriggerUseCase by lazy {
        MeasurementSyncServiceFactory.createUploadTriggerUseCase(
            database = database,
            io = EmptyCoroutineContext,
            remoteProfile = services.runtimeProfile.toSyncRemoteProfile(),
            remoteFactory = DefaultSyncRemoteDataSourceFactory(
                SupabaseSyncRemoteDataSourceProvider(deviceAuthStore = deviceAuthStore),
            ),
            tcpTupleProvider = tcpTupleProviderFor(
                serviceUrl = services.tcpTupleUrl,
                userAgent = services.appSource,
                nowMillis = { clock.now().toEpochMilliseconds() },
            ),
            clock = clock,
        )
    }

    /**
     * A measurement sequence that persists locally and then attempts sync.
     *
     * Built per run rather than cached: the progress listener belongs to one
     * screen's run, and reusing an orchestrator would report a second run's
     * progress to the first screen.
     */
    /**
     * How many stored records have not reached the server.
     */
    suspend fun pendingRecordCount(): Int =
        measurementRepository.getUnsynced().size + submissionRepository.getUnsynced().size

    /**
     * The sync story for a screen to render.
     */
    suspend fun syncStatus(inProgress: Boolean = false): SyncStatusSummary = SyncStatusPresenter.present(
        record = services.syncStatusStore.load(),
        pendingRecords = if (inProgress) 0 else pendingRecordCount(),
        inProgress = inProgress,
        now = clock.now(),
    )

    /**
     * Records the result of an upload attempt.
     *
     * Kept here rather than in the run screen because every future sync
     * trigger - a retry, a background attempt - has to update the same record,
     * and one that only the run screen maintained would go stale.
     */
    fun recordSyncAttempt(uploadedCount: Int, failed: Boolean) {
        val store = services.syncStatusStore
        val previous = store.load()
        val now = clock.now()
        store.save(
            previous.copy(
                lastAttemptAt = now,
                lastAttemptFailed = failed,
                lastSuccessAt = if (uploadedCount > 0) now else previous.lastSuccessAt,
                lastUploadedCount = if (uploadedCount > 0) uploadedCount else previous.lastUploadedCount,
                totalUploadedCount = previous.totalUploadedCount + uploadedCount,
            ),
        )
    }

    /**
     * Measurement locations for the map, most recent first.
     *
     * Nothing supplied these before: the product map homes built a
     * `MapHomeViewModel` and never called `onMeasurementsLoaded`, so the map
     * had no points at all. That is worth separating from the icon defect
     * fixed in 1.2a - with no data, correcting the icon could not have made a
     * pin appear either.
     *
     * One point per measurement, taken from its first recorded location. A
     * measurement records a location at start and end; drawing both would put
     * two pins on top of each other for a stationary user.
     */
    suspend fun recentMeasurementLocations(limit: Long = 500): List<MapHomeMeasurementLocationSnapshot> =
        measurementRepository.getRecent(limit).mapNotNull { measurement ->
            val location = locationRepository.getByMeasurementId(measurement.id).firstOrNull()
                ?: return@mapNotNull null
            MapHomeMeasurementLocationSnapshot(
                id = measurement.id,
                title = measurement.type,
                timestampMs = measurement.timestamp?.toEpochMilliseconds() ?: 0L,
                latitude = location.lat,
                longitude = location.lon,
            )
        }

    /**
     * The submission profile for a run, with the persisted device id attached.
     */
    suspend fun submissionProfile(): FccSubmissionProfile {
        val identity = services.submissionIdentity()
        return FccSubmissionProfile(
            appName = identity.appName,
            appVersion = identity.appVersion,
            deviceId = deviceAuthStore.getDeviceId(),
            provider = identity.provider,
            contactName = identity.contactName,
            contactEmail = identity.contactEmail,
            contactPhone = identity.contactPhone,
        )
    }

    fun createSequenceSyncOrchestrator(
        submissionProfile: FccSubmissionProfile,
        progressListener: MeasurementSequenceProgressListener,
    ): MeasurementSequenceSyncOrchestrator = MeasurementSequenceSyncOrchestrator(
        sequenceOrchestrator = DefaultMsakMeasurementSequenceOrchestratorFactory.create(
            config = services.runtimeProfile.msakConfig.copy(userAgent = services.appSource),
            resultStore = resultStore,
            clock = clock,
            appSource = services.appSource,
            submissionProfile = submissionProfile,
            capabilityProvider = services.capabilityProvider,
            progressListener = progressListener,
        ),
        uploadTriggerUseCase = uploadTriggerUseCase,
    )
}
