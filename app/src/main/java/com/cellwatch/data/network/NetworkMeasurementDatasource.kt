package com.cellwatch.data.network

import android.util.Log
import androidx.annotation.WorkerThread
import com.cellwatch.BuildConfig
import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.FccSubmission
import com.cellwatch.data.model.LatencyData
import com.cellwatch.data.model.Location
import com.cellwatch.data.model.Measurement
import com.cellwatch.data.model.UploadDownloadData
import com.cellwatch.data.model.asEntity
import com.cellwatch.data.model.asNetworkModel
import com.cellwatch.data.network.model.NetworkCell
import com.cellwatch.data.network.model.NetworkFccSubmission
import com.cellwatch.data.network.model.NetworkLatencyData
import com.cellwatch.data.network.model.NetworkLocation
import com.cellwatch.data.network.model.NetworkMeasurement
import com.cellwatch.data.network.model.NetworkMeasurementWithData
import com.cellwatch.data.network.model.NetworkUploadDownloadData
import com.cellwatch.data.network.model.asExternalModel
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.HttpRequestTimeoutException

//class NetworkMeasurementDatasource {
object NetworkMeasurementDatasource {
    private val TAG = this::class.simpleName

    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseApiKey = BuildConfig.SUPABASE_API_KEY

    private val supabaseClient = createSupabaseClient(
            supabaseUrl = supabaseUrl, //"https://xepxxvpbexkyxrwtrgqv.supabase.co",
            supabaseKey = supabaseApiKey
        ) {
            install(Postgrest)
        }

    val measurementTable = supabaseClient.postgrest["measurements"]
    val locationTable = supabaseClient.postgrest["locations"]
    val dataTable = supabaseClient.postgrest["upload_download_data"]
    val latencyTable = supabaseClient.postgrest["latency_data"]
    val cellTable = supabaseClient.postgrest["cells"]
    val fccSubmissionTable = supabaseClient.postgrest["fcc_submissions"]


    @WorkerThread
    suspend fun insertFccSubmission(fccSubmission: FccSubmission): FccSubmission? {
        val insertedFccSubmission: FccSubmission?

        Log.d(TAG, "Attempting to insert measurement ${fccSubmission.id} to Supabase API at $supabaseUrl")
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
    suspend fun insertMeasurement(measurement: Measurement): Measurement? {
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
//                    insertLocation(location)
                }
                insertedMeasurement.locations = insertLocations(locations)
            }

            Log.d(TAG, "measurement.cells = ${measurement.cells?.size.toString()}")
            measurement.cells?.let { cells ->
                // link cells to measurement
                cells.forEach { cell ->
                    Log.d(TAG, "Attempting to upload cell ${cell.id} for measurement ${measurement.id}")
                    cell.measurementId = measurement.id
//                    insertCell(cell)
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
    suspend fun insertMeasurementTransaction(measurement: Measurement): Measurement? {
        var insertedMeasurement: Measurement? = null

        Log.d(TAG, "Attempting to insert measurement ${measurement.id} to Supabase API at ${supabaseUrl}")

        val networkMeasurementData = NetworkMeasurementWithData(
            measurement.asNetworkModel(),
            measurement.uploadDownloadData?.asNetworkModel(),
            measurement.latencyData?.asNetworkModel(),
            measurement.locations?.map { location -> location.asNetworkModel() },
            measurement.cells?.map { cell -> cell.asNetworkModel() }
        )

        try {
//            insertedMeasurement =
//                measurementTable.insert(measurement.asNetworkModel()).decodeSingle<NetworkMeasurement>().asExternalModel()

            insertedMeasurement =
                supabaseClient.postgrest.rpc("insert_measurement", networkMeasurementData).decodeAs<NetworkMeasurement>().asExternalModel()

            Log.d(
                TAG,
                "*** Inserted new Measurement record: $insertedMeasurement"
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


        measurements.map { measurement ->
            Log.d(TAG, "insertMeasurements: $measurement")
        }

        if (measurements.isNotEmpty()) {
            Log.d(TAG, "insertMeasurements: Attempting to upload ${measurements.size} measurements")

            try {
                insertedMeasurements = measurements.map { measurement ->
                    insertMeasurementTransaction(measurement)!!
//                    insertMeasurement(measurement)!!
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadMeasurementsWithData: ${e.message}")
                throw e
            }
        }

        return insertedMeasurements
    }

    @WorkerThread
    suspend fun insertLatencyData(latencyData: LatencyData): LatencyData? {
        var insertedLatencyData: LatencyData? = null

        Log.d(TAG, "insertLatencyData: ${latencyData}")
        try {
            insertedLatencyData =
                latencyTable.insert(latencyData.asNetworkModel()).decodeSingle<NetworkLatencyData>().asExternalModel()
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

        return insertedLatencyData
    }
    
    @WorkerThread
    suspend fun insertUploadDownloadData(uploadDownloadData: UploadDownloadData): UploadDownloadData? {
        var insertedUploadDownloadData: UploadDownloadData?

        try {
            insertedUploadDownloadData =
                dataTable.insert(uploadDownloadData.asNetworkModel()).decodeSingle<NetworkUploadDownloadData>().asExternalModel()
            Log.d(TAG, "*** Inserted new UploadDownloadData record: $insertedUploadDownloadData")
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

        return insertedUploadDownloadData
    }

    @WorkerThread
    suspend fun insertLocation(location: Location): Location? {
        var insertedLocation: Location? = null

        Log.d(TAG, "insertLocation ${location.id}")
        try {
            insertedLocation =
                locationTable.insert(location.asNetworkModel()).decodeSingle<NetworkLocation>().asExternalModel()
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

        return insertedLocation
    }

    @WorkerThread
    suspend fun insertLocations(locations: List<Location>): List<Location>? {
        var insertedLocations: List<Location>? = null

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

        return insertedLocations
    }

    @WorkerThread
    suspend fun insertCell(cell: Cell): Cell? {
        var insertedCell: Cell? = null

        Log.d(TAG, "insertCell ${cell.id}")
        try {
            insertedCell =
                cellTable.insert(cell.asNetworkModel()).decodeSingle<NetworkCell>().asExternalModel()
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

        return insertedCell
    }

    @WorkerThread
    suspend fun insertCells(cells: List<Cell>): List<Cell>? {
        var insertedCells: List<Cell>? = null

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

        return insertedCells
    }

    @WorkerThread
    suspend fun getMeasurements(): List<Measurement>? {
        var measurements: List<Measurement>

        try {
            measurements = measurementTable.select().decodeList<NetworkMeasurement>().map {
                networkMeasurement -> networkMeasurement.asExternalModel()
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

        return measurements
    }

    @WorkerThread
    suspend fun getMeasurementById(measurementId: String): Measurement? {
        var networkMeasurementWithData: NetworkMeasurementWithData?
        var measurement: Measurement?
        var result: PostgrestResult

        try {
            result = measurementTable.select(
                columns = Columns.raw("""*,upload_download_data(*),latency_data(*),locations(*),cells(*)""")
            ) {
                Measurement::id eq measurementId
            }

            Log.d(TAG, "PostgrestResult = ${result.body}")

//            networkMeasurementWithData = measurementTable.select(
//                columns = Columns.raw("""*,upload_download_data(*),latency_data(*),locations(*),cells(*)""")
//            ) {
//                Measurement::id eq measurementId
//            }.decodeSingle<NetworkMeasurementWithData>()
//            measurement = networkMeasurementWithData.asExternalModel()
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

        return null;
    }
}