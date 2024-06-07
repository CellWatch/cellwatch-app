package edu.gatech.cc.cellwatch.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import edu.gatech.cc.cellwatch.core.util.SingletonHolder
import edu.gatech.cc.cellwatch.data.local.dao.CellDao
import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.dao.LatencyDataDao
import edu.gatech.cc.cellwatch.data.local.dao.LocationDao
import edu.gatech.cc.cellwatch.data.local.dao.MeasurementDao
import edu.gatech.cc.cellwatch.data.local.dao.UploadDownloadDataDao
import edu.gatech.cc.cellwatch.data.local.model.CellEntity
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.LatencyDataEntity
import edu.gatech.cc.cellwatch.data.local.model.LocationEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import edu.gatech.cc.cellwatch.data.local.model.UploadDownloadDataEntity
import edu.gatech.cc.cellwatch.data.local.util.InstantConverter
import edu.gatech.cc.cellwatch.data.local.util.ListConverter

@Database(
    entities = [
        FccSubmissionEntity::class,
        MeasurementEntity::class,
        UploadDownloadDataEntity::class,
        LatencyDataEntity::class,
        LocationEntity::class,
        CellEntity::class
    ],
    version = 16,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 15, to = 16)
    ]
)
@TypeConverters(
    InstantConverter::class,
    ListConverter::class
)
abstract class CellWatchDatabase : RoomDatabase() {
    abstract fun fccSubmissionDao(): FccSubmissionDao

    abstract fun measurementDao(): MeasurementDao

    abstract fun uploadDownloadDataDao(): UploadDownloadDataDao

    abstract fun latencyDataDao(): LatencyDataDao

    abstract fun locationDao(): LocationDao

    abstract fun cellDao(): CellDao

    companion object : SingletonHolder<CellWatchDatabase, Context>({
        Room.databaseBuilder(
            it.applicationContext,
            CellWatchDatabase::class.java,
            "cellwatch_database"
        )
            .build()
    })
}
