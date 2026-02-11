package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriverFactory
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseEnvironment
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class AndroidTestSyncDriverFactoryTest {

    private var driver: AndroidSqliteDriver? = null

    @After
    fun tearDown() {
        driver?.close()
    }

    @Test
    fun create_defaultsToLocalTransportTarget() = runBlocking {
        val provider = RecordingEnvironmentProvider()
        val factory = buildFactory(provider)

        factory.create()

        assertEquals(listOf(SyncTransportTarget.LOCAL), provider.transportRequests)
    }

    @Test
    fun create_remoteUsesRemoteTransportTarget() = runBlocking {
        val provider = RecordingEnvironmentProvider()
        val factory = buildFactory(provider)

        factory.create(SupabaseTarget.REMOTE)

        assertEquals(listOf(SyncTransportTarget.REMOTE), provider.transportRequests)
    }

    private fun buildFactory(provider: RecordingEnvironmentProvider): AndroidTestSyncDriverFactory {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sqlDriver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = context,
            name = null,
        )
        driver = sqlDriver
        val db = CellwatchDatabase(sqlDriver)
        return AndroidTestSyncDriverFactory(
            database = db,
            deviceAuthStore = FactoryTestDeviceAuthStore(),
            tcpTupleProvider = object : TcpTupleProvider {
                override suspend fun getPublicTcpTuple(): TcpTuple = TcpTuple(
                    remoteAddress = "203.0.113.20",
                    remotePort = 443,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
            },
            environmentProvider = provider,
            io = EmptyCoroutineContext,
        )
    }
}

private class RecordingEnvironmentProvider : SupabaseEnvironmentProvider {
    val transportRequests = mutableListOf<SyncTransportTarget>()

    override fun resolve(target: SupabaseTarget): SupabaseEnvironment {
        return when (target) {
            SupabaseTarget.LOCAL -> SupabaseEnvironment(
                target = target,
                url = "http://127.0.0.1:54321",
                apiKey = "local-key",
            )
            SupabaseTarget.REMOTE -> SupabaseEnvironment(
                target = target,
                url = "https://example.supabase.co",
                apiKey = "remote-key",
            )
        }
    }

    override fun resolve(target: SyncTransportTarget): SyncSupabaseConfig {
        transportRequests += target
        return when (target) {
            SyncTransportTarget.LOCAL -> SyncSupabaseConfig(
                url = "http://127.0.0.1:54321",
                apiKey = "local-key",
            )
            SyncTransportTarget.REMOTE -> SyncSupabaseConfig(
                url = "https://example.supabase.co",
                apiKey = "remote-key",
            )
        }
    }
}

private class FactoryTestDeviceAuthStore(
    private val deviceId: String = UUID.randomUUID().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
