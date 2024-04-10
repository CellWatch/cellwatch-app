package edu.gatech.cc.cellwatch.data.network

import androidx.annotation.WorkerThread
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.model.NetworkCell
import edu.gatech.cc.cellwatch.data.network.model.NetworkFccSubmission
import edu.gatech.cc.cellwatch.data.network.model.NetworkLatencyData
import edu.gatech.cc.cellwatch.data.network.model.NetworkLocation
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurementWithData
import edu.gatech.cc.cellwatch.data.network.model.NetworkUploadDownloadData
import edu.gatech.cc.cellwatch.data.network.model.asExternalModel
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

object NetworkMeasurementDatasource {
    private val TAG = this::class.simpleName

    private const val supabaseUrl = BuildConfig.SUPABASE_URL
    private const val supabaseApiKey = BuildConfig.SUPABASE_API_KEY

    private val supabaseClient = createSupabaseClient(
            supabaseUrl = supabaseUrl, //"https://xepxxvpbexkyxrwtrgqv.supabase.co",
            supabaseKey = supabaseApiKey
        ) {
            install(Postgrest)
        }

    private val measurementTable = supabaseClient.postgrest["measurements"]
    private val locationTable = supabaseClient.postgrest["locations"]
    private val dataTable = supabaseClient.postgrest["upload_download_data"]
    private val latencyTable = supabaseClient.postgrest["latency_data"]
    private val cellTable = supabaseClient.postgrest["cells"]
    private val fccSubmissionTable = supabaseClient.postgrest["fcc_submissions"]

    @WorkerThread
    suspend fun insertFccSubmissions(fccSubmissions: List<FccSubmission>): List<FccSubmission>? {
        var insertedFccSubmissions: List<FccSubmission>? = null

        if (fccSubmissions.isNotEmpty()) {
            Log.d(TAG, "insertFccSubmissions: Attempting to upload ${fccSubmissions.size} fccSubmissions")
            // Move network IO off the Main thread
            withContext(Dispatchers.IO) {
                try {
                    insertedFccSubmissions = fccSubmissions.map { fccSubmission ->
                        Log.d(TAG, "insertFccSubmissions: $fccSubmission")
                        insertFccSubmission(fccSubmission)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in insertFccSubmissions: ${e.message}")
                    throw e
                }
            }
        }
        
        return insertedFccSubmissions
    }
    
    @WorkerThread
    suspend fun insertFccSubmission(fccSubmission: FccSubmission): FccSubmission {
        val insertedFccSubmission: FccSubmission?

        Log.d(TAG, "Attempting to insert FccSubmission ${fccSubmission.id} to Supabase API at $supabaseUrl")
        try {
            insertedFccSubmission =
                fccSubmissionTable.insert(fccSubmission.asNetworkModel()).decodeSingle<NetworkFccSubmission>().asExternalModel()

            Log.d(
                TAG,
                "*** Inserted new FccSubmission record: $insertedFccSubmission"
            )
        } catch (e: RestException) {
            Log.e(TAG, "RestException: ${e.message}")
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
            throw e
        } catch (e: HttpRequestException) {
            Log.e(TAG, "HttpRequestException: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            throw e
        }

        return insertedFccSubmission
    }

    /**
     * Insert a measurement record and any associated data and locations
     */
    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement): Measurement {
        val insertedMeasurement: Measurement?

        Log.d(TAG, "Attempting to insert measurement ${measurement.id} to Supabase API at $supabaseUrl")

        try {
            insertedMeasurement =
                measurementTable.insert(measurement.asNetworkModel()).decodeSingle<NetworkMeasurement>().asExternalModel()
            Log.d(
                TAG,
                "*** Inserted new Measurement record: $insertedMeasurement"
            )

            measurement.latencyData?.let { latencyData ->
                Log.d(TAG, "Attempt to upload LatencyData ${latencyData.id}")
                latencyData.measurementId = measurement.id
                insertedMeasurement.latencyData = insertLatencyData(latencyData)
                Log.d(TAG, "Uploaded LatencyData: ${insertedMeasurement.latencyData}")
            }

            measurement.uploadDownloadData?.let { uploadDownloadData ->
                Log.d(TAG, "Attempt to upload UploadDownloadData ${uploadDownloadData.id}")
                uploadDownloadData.measurementId = measurement.id
                insertedMeasurement.uploadDownloadData = insertUploadDownloadData(uploadDownloadData)
            }

            Log.d(TAG, "measurement.locations = ${measurement.locations?.size.toString()}")
            measurement.locations?.let { locations ->
                // link locations to measurement
                locations.forEach { location ->
                    Log.d(TAG, "Attempting to upload location ${location.id} for measurement ${measurement.id}")
                    location.measurementId = measurement.id
                }
                insertedMeasurement.locations = insertLocations(locations)
            }

            Log.d(TAG, "measurement.cells = ${measurement.cells?.size.toString()}")
            measurement.cells?.let { cells ->
                // link cells to measurement
                cells.forEach { cell ->
                    Log.d(TAG, "Attempting to upload cell ${cell.id} for measurement ${measurement.id}")
                    cell.measurementId = measurement.id
                }
                insertedMeasurement.cells = insertCells(cells)
            }
        } catch (e: RestException) {
            Log.e(TAG, "RestException: ${e.message}")
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
            throw e
        } catch (e: HttpRequestException) {
            Log.e(TAG, "HttpRequestException: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            throw e
        }

        return insertedMeasurement
    }

    /**
     * Insert a measurement record and any associated data and locations
     */
    @WorkerThread
    suspend fun insertMeasurementTransaction(measurement: Measurement): Measurement {
        val insertedMeasurement: Measurement?

        Log.d(TAG, "Attempting to insert measurement ${measurement.id} to Supabase API at $supabaseUrl")

        if (measurement.cells == null) {
            Log.e(TAG, "Error in insertMeasurementTransaction: measurement.cells is null!!!")
        }

        val networkMeasurementData = NetworkMeasurementWithData(
            measurement.asNetworkModel(),
            measurement.uploadDownloadData?.asNetworkModel(),
            measurement.latencyData?.asNetworkModel(),
            measurement.locations?.map { location -> location.asNetworkModel() },
            measurement.cells?.map { cell -> cell.asNetworkModel() }
        )

        try {
            insertedMeasurement =
                supabaseClient.postgrest.rpc("insert_measurement", networkMeasurementData).decodeAs<NetworkMeasurement>().asExternalModel()

            // Update synchronized status in local Room database
            insertedMeasurement.uploadTime = Clock.System.now()
            CellWatchApp.measurementRepository.updateMeasurement(insertedMeasurement)

            Log.d(
                TAG,
                "*** Inserted new Measurement record as transaction: $insertedMeasurement"
            )
        } catch (e: RestException) {
            Log.e(TAG, "RestException: ${e.message}")
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
            throw e
        } catch (e: HttpRequestException) {
            Log.e(TAG, "HttpRequestException: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            throw e
        }

        return insertedMeasurement
    }

    @WorkerThread
    suspend fun insertMeasurements(measurements: List<Measurement>): List<Measurement>? {
        var insertedMeasurements: List<Measurement>? = null

        if (measurements.isNotEmpty()) {
            Log.d(TAG, "insertMeasurements: Attempting to upload ${measurements.size} measurements")
            // Move network IO off the Main thread
            withContext(Dispatchers.IO) {
                try {
                    insertedMeasurements = measurements.map { measurement ->
                        Log.d(TAG, "insertMeasurements: $measurement")
                        insertMeasurementTransaction(measurement)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in insertMeasurementTransaction: ${e.message}")
                    throw e
                }
            }
        }

        return insertedMeasurements
    }

    @WorkerThread
    suspend fun uploadMeasurements(measurements: List<Measurement>): List<NetworkRestResult> = coroutineScope {
        val deferredResults = measurements.map { measurement ->
            async(Dispatchers.IO) {
                try {
                    Log.d(TAG, "insertMeasurement: $measurement")
                    NetworkRestResult(insertMeasurementTransaction(measurement), null)
                } catch (e: RestException) {
                    Log.e(TAG, "RestException: ${e.error}")
                    // Don't fail on Rest Exceptions
                    NetworkRestResult(measurement, e)
                } catch (e: HttpRequestTimeoutException) {
                    Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                    throw e
                } catch (e: HttpRequestException) {
                    Log.e(TAG, "HttpRequestException: ${e.message}")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Exception: ${e.message}")
                    throw e
                }
            }
        }

        deferredResults.awaitAll()
    }

//    private suspend fun allOrSome(): List<Deferred<Measurement>> =
//        supervisorScope { deferredList() }

//    private fun CoroutineScope.deferredList(): List<Deferred<String>> =
//        List(10) {
//            this.async {
//                delay(it * 10L)
//                if (it != 5) "$it" else throw SillyException()
//            }
//        }


//    class SillyException : Throwable("Silly")

//    @WorkerThread
//    suspend fun insertMeasurementsTest2(measurements: List<Measurement>): List<Measurement> = coroutineScope {
//        val deferredInsertedMeasurements = measurements.map { measurement ->
//            async(Dispatchers.IO) {
//                try {
//                    Log.d(TAG, "insertMeasurement: $measurement")
//                        insertMeasurementTransaction(measurement)
//                } catch (e: RestException) {
//                    Log.e(TAG, "RestException: ${e.error}, ${e.message}")
//                    // Don't fail on Rest Exceptions
//                    measurement
////                    null
//                } catch (e: HttpRequestTimeoutException) {
//                    Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
//                    throw e
//                } catch (e: HttpRequestException) {
//                    Log.e(TAG, "HttpRequestException: ${e.message}")
//                    throw e
//                } catch (e: Exception) {
//                    Log.e(TAG, "Exception: ${e.message}")
//                    throw e
//                }
//            }
//        }
//
////        deferredInsertedMeasurements.awaitAll().filterNotNull()
//        deferredInsertedMeasurements.awaitAll()
//    }

//    @WorkerThread
//    suspend fun insertMeasurementsTest(measurements: List<Measurement>): List<Measurement>? {
//        var insertedMeasurements: List<Measurement>? = null
//
//        val scope = CoroutineScope(ceh + Dispatchers.IO)
//        var index = 0
//
//        Log.d(TAG, "Inserting ${measurements.size} measurements")
//
//        scope.launch {
////            try {
//                Log.d(TAG, "launch coroutine...")
//                insertedMeasurements = supervisorScope {
//                    Log.d(TAG, "launch supervisorScope...")
//
//                    withContext(Dispatchers.IO) {
//                        measurements.mapNotNull { measurement ->
//                            try {
//                                Log.d(TAG, "insertMeasurements: $measurement")
//                                if (index != 2) {
//                                    index++
//                                    insertMeasurementTransaction(measurement)
//                                } else throw SillyException()
//                            } catch (e: Throwable) {
//                                Log.e(TAG, "Error in insertMeasurementTransaction: ${e.message}")
//                                null
//                            }
//                        }
//                    }
//                }
//        }
//
////        withContext(Dispatchers.IO) {
////            Thread.sleep(1000)
////        }
//
//        insertedMeasurements?.map {
//            it.uploadTime = Clock.System.now()
//            Log.d(TAG, "insertedMeasurement: $it")
//        }
//
//        return insertedMeasurements
//    }

    val ceh = CoroutineExceptionHandler { _, e ->
        println("Handled Crash $e")
    }

    @WorkerThread
    suspend fun insertLatencyData(latencyData: LatencyData): LatencyData? {
        var insertedLatencyData: LatencyData? = null

        Log.d(TAG, "insertLatencyData: $latencyData")

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                insertedLatencyData =
                    latencyTable.insert(latencyData.asNetworkModel())
                        .decodeSingle<NetworkLatencyData>().asExternalModel()
                Log.d(TAG, "*** Inserted new LatencyData record: $insertedLatencyData")
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        return insertedLatencyData
    }
    
    @WorkerThread
    suspend fun insertUploadDownloadData(uploadDownloadData: UploadDownloadData): UploadDownloadData? {
        var insertedUploadDownloadData: UploadDownloadData?

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "insertUploadDownloadData: ${uploadDownloadData.asNetworkModel()}")
                insertedUploadDownloadData =
                    dataTable.insert(uploadDownloadData.asNetworkModel())
                        .decodeSingle<NetworkUploadDownloadData>().asExternalModel()
                Log.d(
                    TAG,
                    "*** Inserted new UploadDownloadData record: $insertedUploadDownloadData"
                )
            } catch (e: RestException) {
                Log.e(TAG, "RestException in insertUploadDownloadData: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException in insertUploadDownloadData: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException in insertUploadDownloadData: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception in insertUploadDownloadData: ${e.message}")
                throw e
            }
        }

        return insertedUploadDownloadData
    }

    @WorkerThread
    suspend fun insertLocation(location: Location): Location? {
        var insertedLocation: Location? = null

        Log.d(TAG, "insertLocation ${location.id}")
        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                insertedLocation =
                    locationTable.insert(location.asNetworkModel()).decodeSingle<NetworkLocation>()
                        .asExternalModel()
                Log.d(TAG, "*** Inserted new Location record: $insertedLocation")
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        return insertedLocation
    }

    @WorkerThread
    suspend fun insertLocations(locations: List<Location>): List<Location>? {
        var insertedLocations: List<Location>? = null

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                insertedLocations = locations.map { location ->
                    insertLocation(location)!!
                }
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        return insertedLocations
    }

    @WorkerThread
    suspend fun insertCell(cell: Cell): Cell? {
        var insertedCell: Cell? = null

        Log.d(TAG, "insertCell ${cell.id}")

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                insertedCell =
                    cellTable.insert(cell.asNetworkModel()).decodeSingle<NetworkCell>()
                        .asExternalModel()
                Log.d(TAG, "*** Inserted new Cell record: $insertedCell")
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        return insertedCell
    }

    @WorkerThread
    suspend fun insertCells(cells: List<Cell>): List<Cell>? {
        var insertedCells: List<Cell>? = null

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                insertedCells = cells.map { cell ->
                    insertCell(cell)!!
                }
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        return insertedCells
    }

    @WorkerThread
    suspend fun getMeasurements(): List<Measurement> {
        var measurements: List<Measurement>

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                measurements = measurementTable.select().decodeList<NetworkMeasurement>()
                    .map { networkMeasurement ->
                        networkMeasurement.asExternalModel()
                    }
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        return measurements
    }

    @WorkerThread
    suspend fun getMeasurementById(measurementId: String): Measurement? {
        var result: PostgrestResult

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                result = measurementTable.select(
                    columns = Columns.raw("""*,upload_download_data(*),latency_data(*),locations(*),cells(*)""")
                ) {
                    Measurement::id eq measurementId
                }

                Log.d(TAG, "getMeasurementById: PostgrestResult = ${result.body}")
            } catch (e: RestException) {
                Log.e(TAG, "RestException: ${e.message}")
                throw e
            } catch (e: HttpRequestTimeoutException) {
                Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
                throw e
            } catch (e: HttpRequestException) {
                Log.e(TAG, "HttpRequestException: ${e.message}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                throw e
            }
        }

        // return Measurement or null if not found
        return result.decodeSingleOrNull<NetworkMeasurement>()?.asExternalModel()
    }
}
