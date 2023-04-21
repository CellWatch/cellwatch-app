package com.example.ndt8

import android.app.Application
import com.example.ndt8.data.CellWatchDatabase
import com.example.ndt8.data.repository.MeasurementRepository

class CellWatchApp : Application() {
    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { CellWatchDatabase.getDatabase(this) }
    val measurementRepository by lazy { MeasurementRepository(database.measurementDao()) }
}