package edu.gatech.cc.cellwatch.data.network

import edu.gatech.cc.cellwatch.data.model.Measurement
import io.github.jan.supabase.exceptions.RestException

data class NetworkRestResult(
    val measurement: Measurement,
    val error: RestException? = null
)

//data class NetworkRestResult(
//    val measurements: List<Measurement>,
//    val errors: List<Exception>
//)