package com.example.ndt8.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.ndt8.data.entities.LatencyData
import com.example.ndt8.data.entities.Location
import com.example.ndt8.data.entities.Measurement
import com.example.ndt8.data.entities.UploadDownloadData
import com.example.ndt8.data.repository.LatencyDataDao
import com.example.ndt8.data.repository.LocationDao
import com.example.ndt8.data.repository.MeasurementDao
import com.example.ndt8.data.repository.UploadDownloadDataDao
import com.example.ndt8.data.util.InstantConverter
import com.example.ndt8.data.util.ListConverter

@Database(
    entities = [
        Measurement::class,
        UploadDownloadData::class,
        LatencyData::class,
        Location::class
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(
    InstantConverter::class,
    ListConverter::class
)
public abstract class CellWatchDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    abstract fun uploadDownloadDataDao(): UploadDownloadDataDao

    abstract fun latencyDataDao(): LatencyDataDao

    abstract fun locationDao(): LocationDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time. 
        @Volatile
        private var INSTANCE: CellWatchDatabase? = null

        fun getDatabase(context: Context): CellWatchDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CellWatchDatabase::class.java,
                    "word_database"
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}