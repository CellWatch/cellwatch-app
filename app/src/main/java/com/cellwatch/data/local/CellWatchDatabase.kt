package com.cellwatch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cellwatch.core.util.SingletonHolder
import com.cellwatch.data.local.model.LatencyDataEntity
import com.cellwatch.data.local.model.LocationEntity
import com.cellwatch.data.local.model.MeasurementEntity
import com.cellwatch.data.local.model.UploadDownloadDataEntity
import com.cellwatch.data.local.dao.LatencyDataDao
import com.cellwatch.data.local.dao.LocationDao
import com.cellwatch.data.local.dao.MeasurementDao
import com.cellwatch.data.local.dao.UploadDownloadDataDao
import com.cellwatch.data.local.util.InstantConverter
import com.cellwatch.data.local.util.ListConverter

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

    companion object : com.cellwatch.core.util.SingletonHolder<CellWatchDatabase, Context>({
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