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
import edu.gatech.cc.cellwatch.domain.export.ExportContact
import edu.gatech.cc.cellwatch.domain.export.ExtendedExportApp
import edu.gatech.cc.cellwatch.domain.export.ExtendedExportBundle
import edu.gatech.cc.cellwatch.domain.export.ExtendedExportSummary
import edu.gatech.cc.cellwatch.domain.export.FccSubmissionExportBundle
import edu.gatech.cc.cellwatch.domain.export.deriveFccOutcome
import edu.gatech.cc.cellwatch.domain.export.toExtendedExportRun
import edu.gatech.cc.cellwatch.domain.export.toFccSubmissionExport
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionOutcomeMessage
import kotlinx.serialization.json.Json
import edu.gatech.cc.cellwatch.domain.fcc.DefaultMsakMeasurementSequenceOrchestratorFactory
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionProfile
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceProgressListener
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeMeasurementLocationSnapshot
import edu.gatech.cc.cellwatch.domain.measurementhistory.MeasurementHistoryRunSnapshot
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementResultReadModelUseCase
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunState
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.onboarding.OnboardingProfileStore
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

    /** The saved profile, which carries the user's collection-mode choice. */
    val onboardingStore: OnboardingProfileStore

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
 * Everything the history screen needs, fetched together.
 *
 * One call rather than three: the run list, the pending counts and the sync
 * summary are read from the same database moments apart, and fetching them
 * separately let the screen show a run as pending while the summary already
 * counted it as uploaded.
 */
data class ProductHistorySnapshot(
    val runs: List<MeasurementHistoryRunSnapshot>,
    val syncSummary: SyncStatusSummary,
    val pendingMeasurements: Int,
    val pendingSubmissions: Int,
)

/**
 * Read-only facts the settings screen reports.
 *
 * Exists because the answers to "which server am I talking to" and "what is my
 * device id" were previously only obtainable from the harness, which a real
 * user does not have. They are the first things anyone asks for when a
 * measurement does not arrive.
 */
data class ProductDiagnostics(
    val appName: String,
    val appVersion: String,
    val deviceId: String,
    val msakMode: String,
    val msakEndpoint: String,
    val supabaseMode: String,
    val collectionMode: CollectionMode,
)

/**
 * A ready-to-write export: the platform only has to put it somewhere.
 */
data class ExportDocument(
    val fileName: String,
    val json: String,
    val recordCount: Int,
)

/**
 * Composition root for the product app. One per process; screens receive it.
 */
class ProductContainer(
    private val services: ProductPlatformServices,
    private val clock: Clock = Clock.System,
) {
    /**
     * `explicitNulls` matters: the FCC specification conditions nullability on
     * failure, so an absent field and a null field are different statements
     * and the nulls have to be written out.
     */
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    private companion object {
        const val SUBMISSION_CATEGORY = "Consumer Challenge"
    }

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
     * Stored runs, newest first, as history rows.
     *
     * Formatting goes through [MeasurementResultReadModelUseCase] - the same
     * one the results screen uses - so a run cannot read "17.2 Mbps" when it
     * finishes and something else in history.
     */
    suspend fun recentRuns(limit: Long = 200): List<MeasurementHistoryRunSnapshot> {
        // Measurements without a group are dropped: history lists runs, and a
        // measurement with no run to belong to cannot be presented as one.
        val byGroup = measurementRepository.getRecent(limit)
            .filter { it.groupId != null }
            .groupBy { it.groupId!! }
        return byGroup.mapNotNull { (groupId, measurements) ->
            val timestamp = measurements.mapNotNull { it.timestamp }.maxOrNull() ?: return@mapNotNull null
            // Hydrated from the data tables before grouping. getRecent returns
            // bare measurement rows - latency and throughput figures live in
            // their own tables - and MeasurementGroup rejects a measurement
            // whose data is absent, so grouping the raw rows threw outright.
            val group = runCatching {
                MeasurementGroup(
                    latency = hydrate(measurements.firstOrNull { it.type == "latency" }),
                    download = hydrate(measurements.firstOrNull { it.type == "download" }),
                    upload = hydrate(measurements.firstOrNull { it.type == "upload" }),
                    submission = null,
                    id = groupId,
                )
            }.getOrElse { return@mapNotNull null }
            val uploadedAt = measurements.mapNotNull { it.uploadTime }.maxOrNull()
            val read = MeasurementResultReadModelUseCase().present(
                MeasurementRunState(
                    progress = MeasurementRunProgress.END,
                    results = group,
                    // A run counts as uploaded only when every one of its
                    // measurements has: a partial upload is still pending.
                    uploadTime = uploadedAt.takeIf {
                        measurements.isNotEmpty() && measurements.all { m -> m.uploadTime != null }
                    },
                ),
            )
            MeasurementHistoryRunSnapshot(
                timestampMs = timestamp.toEpochMilliseconds(),
                latency = read.latencyText,
                download = read.downloadText,
                upload = read.uploadText,
                uploaded = read.uploadedText,
                detail = read.summaryText,
            )
        }.sortedByDescending { it.timestampMs }
    }

    suspend fun historySnapshot(): ProductHistorySnapshot {
        val (pendingMeasurements, pendingSubmissions) = pendingCounts()
        return ProductHistorySnapshot(
            runs = recentRuns(),
            syncSummary = SyncStatusPresenter.present(
                record = services.syncStatusStore.load(),
                pendingRecords = pendingMeasurements + pendingSubmissions,
                inProgress = false,
                now = clock.now(),
            ),
            pendingMeasurements = pendingMeasurements,
            pendingSubmissions = pendingSubmissions,
        )
    }

    /**
     * Uploads whatever is still pending, outside a measurement run.
     *
     * The upload trigger's map-start entry point is reused deliberately: a
     * second upload path would be a second set of rules about what is eligible.
     */
    suspend fun retryPendingUploads(): ProductHistorySnapshot {
        val report = runCatching { uploadTriggerUseCase.onMapStart() }
        val uploaded = report.getOrNull()
            ?.let { it.measurements.uploaded + it.submissions.uploaded }
            ?: 0
        recordSyncAttempt(uploadedCount = uploaded, failed = report.isFailure || uploaded == 0)
        return historySnapshot()
    }

    suspend fun diagnostics(): ProductDiagnostics {
        val identity = services.submissionIdentity()
        val profile = services.runtimeProfile
        return ProductDiagnostics(
            appName = identity.appName,
            appVersion = identity.appVersion,
            deviceId = deviceAuthStore.getDeviceId(),
            msakMode = profile.msakMode.name,
            // The local host when there is one, otherwise the public locate
            // service - saying "PUBLIC" twice tells the reader nothing.
            msakEndpoint = profile.msakConfig.localServerHost ?: "M-Lab Locate",
            supabaseMode = profile.supabaseMode.name,
            collectionMode = collectionMode(),
        )
    }

    /**
     * The mode measurements run in, from the saved profile.
     *
     * The shells hardcoded FCC_CHALLENGE. Collection mode decides whether a
     * submission is created at all, so a user who chose TESTING in settings
     * was still having submissions built for them - the setting did nothing.
     */
    fun collectionMode(): CollectionMode =
        services.onboardingStore.loadProfile()?.collectionMode ?: CollectionMode.FCC_CHALLENGE

    /**
     * Attaches a measurement's own latency or throughput row.
     *
     * Returns null when the data is missing rather than substituting an empty
     * record: an incomplete run should drop out of history, not appear with
     * invented zeroes.
     */
    private suspend fun hydrate(measurement: Measurement?): Measurement? {
        if (measurement == null) return null
        return when (measurement.type) {
            "latency" -> latencyRepository.getByMeasurementId(measurement.id).firstOrNull()
                ?.let { measurement.copy(latencyData = it) }
            "download", "upload" -> uploadDownloadRepository.getByMeasurementId(measurement.id)
                .firstOrNull()?.let { measurement.copy(uploadDownloadData = it) }
            else -> null
        }
    }

    /**
     * Adds the locations and cells an export needs.
     *
     * Separate from [hydrate] because history does not need them and they are
     * two more queries per measurement; the export is the only caller that
     * reads them.
     */
    private suspend fun withObservations(measurement: Measurement): Measurement = measurement.copy(
        locations = locationRepository.getByMeasurementId(measurement.id),
        cells = cellRepository.getByMeasurement(measurement.id),
    )

    /**
     * Every stored run, hydrated, newest first.
     *
     * Returns groups rather than export documents so both exports read the
     * same source: an FCC file and an extended file that disagreed about the
     * same measurement would be worse than either alone.
     */
    private suspend fun exportGroups(limit: Long = 1_000): List<Pair<MeasurementGroup, Measurement?>> {
        val byGroup = measurementRepository.getRecent(limit)
            .filter { it.groupId != null }
            .groupBy { it.groupId!! }
        return byGroup.mapNotNull { (groupId, measurements) ->
            val hydrated = measurements.mapNotNull { hydrate(it) }.map { withObservations(it) }
            val group = runCatching {
                MeasurementGroup(
                    latency = hydrated.firstOrNull { it.type == "latency" },
                    download = hydrated.firstOrNull { it.type == "download" },
                    upload = hydrated.firstOrNull { it.type == "upload" },
                    submission = submissionRepository.getById(groupId),
                    id = groupId,
                )
            }.getOrElse { return@mapNotNull null }
            group to hydrated.firstOrNull()
        }.sortedByDescending { (_, first) -> first?.timestamp?.toEpochMilliseconds() ?: 0L }
    }

    /** The FCC bulk-submission document. Empty when nothing qualified. */
    suspend fun exportFccJson(): ExportDocument {
        val identity = services.submissionIdentity()
        val submissions = exportGroups().mapNotNull { (group, _) -> group.toFccSubmissionExport() }
        val bundle = FccSubmissionExportBundle(
            contact = ExportContact(
                name = identity.contactName,
                email = identity.contactEmail,
                phone = identity.contactPhone,
            ),
            submission_category = SUBMISSION_CATEGORY,
            submissions = submissions,
        )
        return ExportDocument(
            fileName = "cellwatch-fcc-${clock.now().toEpochMilliseconds()}.json",
            json = exportJson.encodeToString(FccSubmissionExportBundle.serializer(), bundle),
            recordCount = submissions.size,
        )
    }

    /** Everything recorded, qualifying or not, with the reason it did not. */
    suspend fun exportExtendedJson(): ExportDocument {
        val identity = services.submissionIdentity()
        val diagnostics = diagnostics()
        val groups = exportGroups()
        val runs = groups.map { (group, _) ->
            group.toExtendedExportRun(
                fccOutcome = group.deriveFccOutcome(
                    challengeMode = diagnostics.collectionMode == CollectionMode.FCC_CHALLENGE,
                ),
            )
        }
        val bundle = ExtendedExportBundle(
            exported_at = clock.now().toString(),
            app = ExtendedExportApp(
                name = identity.appName,
                version = identity.appVersion,
                device_id = diagnostics.deviceId,
                platform = services.appSource,
                msak_mode = diagnostics.msakMode,
                msak_endpoint = diagnostics.msakEndpoint,
                upload_target = diagnostics.supabaseMode,
                collection_mode = diagnostics.collectionMode.name,
            ),
            contact = ExportContact(
                name = identity.contactName,
                email = identity.contactEmail,
                phone = identity.contactPhone,
            ),
            summary = ExtendedExportSummary(
                run_count = runs.size,
                measurement_count = runs.sumOf { it.measurements.size },
                submitted_run_count = runs.count { it.submitted_to_fcc },
                unsynced_measurement_count = measurementRepository.getUnsynced().size,
            ),
            runs = runs,
        )
        return ExportDocument(
            fileName = "cellwatch-extended-${clock.now().toEpochMilliseconds()}.json",
            json = exportJson.encodeToString(ExtendedExportBundle.serializer(), bundle),
            recordCount = runs.size,
        )
    }

    /** Unsynced counts, split the way the history screen reports them. */
    suspend fun pendingCounts(): Pair<Int, Int> =
        measurementRepository.getUnsynced().size to submissionRepository.getUnsynced().size

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
     * One point per *run*, not per measurement, taken from the first location
     * of the run's first measurement.
     *
     * Per-measurement would triple-count: a run is latency, download and
     * upload, so three rows at one spot. That put three pins on top of each
     * other and made the hex count read 9 where the user had taken 3 - which
     * is also what frozenApp counted, group ids rather than measurements.
     *
     * Within a measurement only the first location is used; a measurement
     * records one at start and one at end.
     */
    suspend fun recentMeasurementLocations(limit: Long = 500): List<MapHomeMeasurementLocationSnapshot> =
        measurementRepository.getRecent(limit)
            .filter { it.groupId != null }
            .groupBy { it.groupId!! }
            .mapNotNull { (groupId, measurements) ->
                val ordered = measurements.sortedBy { it.timestamp?.toEpochMilliseconds() ?: 0L }
                val located = ordered.firstNotNullOfOrNull { measurement ->
                    locationRepository.getByMeasurementId(measurement.id).firstOrNull()
                        ?.let { measurement to it }
                } ?: return@mapNotNull null
                val (measurement, location) = located
                MapHomeMeasurementLocationSnapshot(
                    id = groupId,
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
