package com.cellwatch

import android.app.Application
import android.content.Context
import com.cellwatch.data.local.CellWatchDatabase
import com.cellwatch.data.core.repositories.MeasurementRepository
import com.cellwatch.data.network.NetworkMeasurementDatasource

class CellWatchApp : Application() {
    init {
        com.cellwatch.CellWatchApp.Companion.instance = this
    }

//    private val database by lazy { CellWatchDatabase.getInstance(applicationContext()) }

//    val measurementRepository by lazy {
//        MeasurementRepository(
//            database.measurementDao(),
//            NetworkMeasurementDatasource
//        )
//    }

    companion object {
        private var instance: com.cellwatch.CellWatchApp? = null

        fun applicationContext() : Context {
            return com.cellwatch.CellWatchApp.Companion.instance!!.applicationContext
        }
        // Using by lazy so the database and the repository are only created when they're needed
        // rather than when the application starts
        private val database by lazy { CellWatchDatabase.getInstance(com.cellwatch.CellWatchApp.Companion.applicationContext()) }
        val measurementRepository by lazy {
            com.cellwatch.data.core.repositories.MeasurementRepository(
                com.cellwatch.CellWatchApp.Companion.database.measurementDao(),
                NetworkMeasurementDatasource
            )
        }
//        private val networkMeasurementDatasource by lazy { NetworkMeasurementDatasource() }
//        val measurementNetworkRepository by lazy { MeasurementNetworkRepository(networkMeasurementDatasource) }
    }

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
//    val database by lazy { CellWatchDatabase.getDatabase(this) }
//    val measurementRepository by lazy { MeasurementRepository(database.measurementDao()) }
}