package com.example.ndt8.data.network

import android.util.Log
import androidx.annotation.WorkerThread
import com.example.ndt8.BuildConfig
import com.example.ndt8.data.core.model.asNetworkModel
import com.example.ndt8.data.local.model.LatencyDataEntity
import com.example.ndt8.data.local.model.LocationEntity
import com.example.ndt8.data.local.model.MeasurementEntity
import com.example.ndt8.data.local.model.UploadDownloadDataEntity
import com.example.ndt8.data.model.LatencyData
import com.example.ndt8.data.model.Location
import com.example.ndt8.data.model.Measurement
import com.example.ndt8.data.model.UploadDownloadData
import com.example.ndt8.data.model.asNetworkModel
import com.example.ndt8.data.network.model.NetworkLatencyData
import com.example.ndt8.data.network.model.NetworkLocation
import com.example.ndt8.data.network.model.NetworkMeasurement
import com.example.ndt8.data.network.model.NetworkUploadDownloadData
import com.example.ndt8.data.network.model.asExternalModel
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException

//class NetworkMeasurementDatasource {
object NetworkMeasurementDatasource {
    private val TAG = this::class.simpleName

//    companion object : SingletonHolder<MeasurementNetworkDatasource, Context>(::MeasurementNetworkDatasource) {
//    companion object {
//        @Volatile
//        private var instance: NetworkMeasurementDatasource? = null
//
//        fun getInstance(): NetworkMeasurementDatasource {
//            if (instance == null) {
//                synchronized(this) {
//                    if (instance == null) {
//                        instance = NetworkMeasurementDatasource()
//                    }
//                }
//            }
//            return instance!!
//        }

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
//    }

    /**
     * Insert a measurement record and any associated data and locations
     */
    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement): Measurement? {
        var insertedMeasurement: Measurement? = null

        Log.d(TAG, "Attempting to insert measurement ${measurement.id}")

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
            Log.d(TAG, "uploadMeasurementsWithData: Attempting to upload ${measurements.size} measurements")

            try {
                insertedMeasurements = measurements.map { measurement ->
                    insertMeasurement(measurement)!!
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

//    @WorkerThread
//    suspend fun insertMeasurementWithLocationsAndData(
//        measurement: MeasurementEntity,
//        locationEntities: List<LocationEntity>?,
//        uploadDownloadDataEntity: UploadDownloadDataEntity?
//    ): MeasurementEntity? {
//        var insertedMeasurement: MeasurementEntity?
//
//        try {
//            insertedMeasurement =
//                measurementTable.insert(measurement.asNetworkModel()).decodeSingle<MeasurementEntity>()
//
//            locationEntities?.map {
//                it.measurementId = insertedMeasurement.id
//                locationTable.insert(it.asNetworkModel())
//            }
//
//            uploadDownloadDataEntity?.let {
//                it.measurementId = insertedMeasurement.id
//                dataTable.insert(it.asNetworkModel())
//            }
//        } catch (e: RestException) {
//            Log.e(TAG, "RestException: ${e.message}")
//            throw e
//        } catch (e: HttpRequestTimeoutException) {
//            Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
//            throw e
//        } catch (e: HttpRequestException) {
//            Log.e(TAG, "HttpRequestException: ${e.message}")
//            throw e
//        } catch (e: Exception) {
//            Log.e(TAG, "Exception: ${e.message}")
//            throw e
//        }
//
//        return insertedMeasurement
//    }

//    @WorkerThread
//    suspend fun insertMeasurementWithLocationsAndLatency(
//        measurement: MeasurementEntity,
//        locationEntities: List<LocationEntity>?,
//        latencyDataEntity: LatencyDataEntity?
//    ): MeasurementEntity? {
//        var insertedMeasurement: MeasurementEntity?
//
//        try {
//            insertedMeasurement =
//                measurementTable.insert(measurement.asNetworkModel()).decodeSingle<MeasurementEntity>()
//
//            locationEntities?.map {
//                it.measurementId = insertedMeasurement.id
//                locationTable.insert(it.asNetworkModel())
//            }
//
//            latencyDataEntity?.let {
//                it.measurementId = insertedMeasurement.id
//                latencyTable.insert(it.asNetworkModel())
//            }
//        } catch (e: RestException) {
//            Log.e(TAG, "RestException: ${e.message}")
//            throw e
//        } catch (e: HttpRequestTimeoutException) {
//            Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
//            throw e
//        } catch (e: HttpRequestException) {
//            Log.e(TAG, "HttpRequestException: ${e.message}")
//            throw e
//        } catch (e: Exception) {
//            Log.e(TAG, "Exception: ${e.message}")
//            throw e
//        }
//
//        return insertedMeasurement
//    }
}