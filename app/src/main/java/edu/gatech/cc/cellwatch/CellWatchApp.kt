package edu.gatech.cc.cellwatch

import android.app.Application
import android.content.Context
import edu.gatech.cc.cellwatch.data.core.repositories.FccSubmissionRepository
import edu.gatech.cc.cellwatch.data.local.CellWatchDatabase
import edu.gatech.cc.cellwatch.data.core.repositories.MeasurementRepository
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource

class CellWatchApp : Application() {
    init {
        instance = this
    }

    companion object {
        private var instance: CellWatchApp? = null

        fun applicationContext() : Context {
            return instance!!.applicationContext
        }
        // Using by lazy so the database and the repository are only created when they're needed
        // rather than when the application starts
        private val database by lazy { CellWatchDatabase.getInstance(applicationContext()) }
        val fccSubmissionRepository by lazy {
            FccSubmissionRepository(
                database.fccSubmissionDao(),
                NetworkMeasurementDatasource
            )
        }
        val measurementRepository by lazy {
            MeasurementRepository(
                database.measurementDao(),
                NetworkMeasurementDatasource
            )
        }
        val localDataStore by lazy {
            LocalDataStore(applicationContext())
        }
    }
}
