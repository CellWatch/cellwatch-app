package com.example.ndt8

import android.app.Application
import android.content.Context
import com.example.ndt8.data.local.CellWatchDatabase
import com.example.ndt8.data.core.repositories.MeasurementRepository
import com.example.ndt8.data.network.NetworkMeasurementDatasource
import com.example.ndt8.data.network.repositories.MeasurementNetworkRepository

class CellWatchApp : Application() {
    init {
        instance = this
    }

//    private val database by lazy { CellWatchDatabase.getInstance(applicationContext()) }

//    val measurementRepository by lazy {
//        MeasurementRepository(
//            database.measurementDao(),
//            NetworkMeasurementDatasource
//        )
//    }

    companion object {
        private var instance: CellWatchApp? = null

        fun applicationContext() : Context {
            return instance!!.applicationContext
        }
        // Using by lazy so the database and the repository are only created when they're needed
        // rather than when the application starts
        private val database by lazy { CellWatchDatabase.getInstance(applicationContext()) }
        val measurementRepository by lazy {
            MeasurementRepository(
                database.measurementDao(),
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