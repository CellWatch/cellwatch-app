package com.example.ndt8.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.ndt8.core.util.SingletonHolder
import com.example.ndt8.data.local.model.LatencyDataEntity
import com.example.ndt8.data.local.model.LocationEntity
import com.example.ndt8.data.local.model.MeasurementEntity
import com.example.ndt8.data.local.model.UploadDownloadDataEntity
import com.example.ndt8.data.local.dao.LatencyDataDao
import com.example.ndt8.data.local.dao.LocationDao
import com.example.ndt8.data.local.dao.MeasurementDao
import com.example.ndt8.data.local.dao.UploadDownloadDataDao
import com.example.ndt8.data.local.util.InstantConverter
import com.example.ndt8.data.local.util.ListConverter

@Database(
    entities = [
        MeasurementEntity::class,
        UploadDownloadDataEntity::class,
        LatencyDataEntity::class,
        LocationEntity::class
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(
    InstantConverter::class,
    ListConverter::class
)
abstract class CellWatchDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    abstract fun uploadDownloadDataDao(): UploadDownloadDataDao

    abstract fun latencyDataDao(): LatencyDataDao

    abstract fun locationDao(): LocationDao

    companion object : SingletonHolder<CellWatchDatabase, Context>({
        Room.databaseBuilder(
                    it.applicationContext,
                    CellWatchDatabase::class.java,
                    "cellwatch_database"
                ).fallbackToDestructiveMigration().build()
    })

//    companion object {
//        // Singleton prevents multiple instances of database opening at the
//        // same time.
//        @Volatile
//        private var INSTANCE: CellWatchDatabase? = null
//
//        fun getDatabase(context: Context): CellWatchDatabase {
//            // if the INSTANCE is not null, then return it,
//            // if it is, then create the database
//            return INSTANCE ?: synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    CellWatchDatabase::class.java,
//                    "cellwatch_database"
//                ).fallbackToDestructiveMigration().build()
//                INSTANCE = instance
//                // return instance
//                instance
//            }
//        }
//    }
}