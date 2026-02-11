package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.data.local.CellWatchDatabase
import edu.gatech.cc.cellwatch.data.remote.SupabaseConnectionConfig
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncService

object AndroidSharedSyncServiceFactory {
    fun create(
        database: CellWatchDatabase,
        localDataStore: edu.gatech.cc.cellwatch.data.datastore.LocalDataStore,
        userAgent: String,
    ): MeasurementSyncService {
        val localStore = RoomMeasurementSyncLocalStore(
            measurementDao = database.measurementDao(),
            submissionDao = database.fccSubmissionDao(),
        )
        val remote = SupabaseMeasurementSyncRemoteDataSource(
            config = SupabaseConnectionConfig(
                url = BuildConfig.SUPABASE_URL,
                apiKey = BuildConfig.SUPABASE_API_KEY,
            ),
            deviceAuthStore = AndroidDeviceAuthStore(localDataStore),
        )
        val tcpTupleProvider = AndroidTcpTupleProvider(
            serviceUrl = BuildConfig.TCP_TUPLE_URL,
            userAgent = userAgent,
        )

        return MeasurementSyncServiceFactory.create(
            localStore = localStore,
            remoteDataSource = remote,
            tcpTupleProvider = tcpTupleProvider,
        )
    }
}
