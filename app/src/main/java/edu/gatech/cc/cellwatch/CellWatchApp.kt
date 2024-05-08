package edu.gatech.cc.cellwatch

import android.app.Application
import android.content.Context
import edu.gatech.cc.cellwatch.data.core.repositories.MeasurementRepository
import edu.gatech.cc.cellwatch.data.core.repositories.SettingsRepository
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.data.local.CellWatchDatabase
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import org.conscrypt.Conscrypt
import java.security.Security

class CellWatchApp : Application() {
    init {
        instance = this

        // Using Conscrypt somehow makes the CountableSocket work with TLS sockets --
        // it doesn't otherwise. I found a project on GitHub trying to count socket bytes
        // (https://github.com/dave-r12/okhttp-byte-counter) and then found a linked
        // issue (https://github.com/google/conscrypt/issues/65) that suggests Conscrypt
        // might eventually solve the problem but hasn't yet. I guess it has now...
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

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
                database.fccSubmissionDao(),
                NetworkMeasurementDatasource
            )
        }
        private val localDataStore by lazy {
            LocalDataStore(applicationContext())
        }
        val settingsRepository by lazy {
            SettingsRepository(localDataStore)
        }

        val userAgent = "CellWatch/${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) "-debug" else ""}"
    }
}
