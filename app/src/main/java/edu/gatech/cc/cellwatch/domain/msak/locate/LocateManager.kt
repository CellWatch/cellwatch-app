package edu.gatech.cc.cellwatch.domain.msak.locate

import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_AUTHORIZE_PATH
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_RESULT_PATH
import edu.gatech.cc.cellwatch.domain.msak.LOCATE_LATENCY_PATH
import edu.gatech.cc.cellwatch.domain.msak.LOCATE_THROUGHPUT_PATH
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.THROUGHPUT_DOWNLOAD_PATH
import edu.gatech.cc.cellwatch.domain.msak.THROUGHPUT_UPLOAD_PATH
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.InvalidParameterException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class LocateManager(client: OkHttpClient? = null, private val locateUrl: String? = null) {
    private val TAG = this::class.simpleName
    private val msakServerEnv = BuildConfig.MSAK_SERVER_ENV
    private val msakLocalServerHost = BuildConfig.MSAK_LOCAL_SERVER_HOST
    private val msakLocalServerSecure = BuildConfig.MSAK_LOCAL_SERVER_SECURE
    private val locateUrls = mapOf(
        "prod" to "https://locate.measurementlab.net/v2/nearest/",
        "staging" to "https://locate-dot-mlab-staging.appspot.com/v2/nearest/"
    )
    private val client = client ?: OkHttpClient.Builder().build()

    suspend fun locateThroughputServers(server: Server? = null): List<Server> {
        return locateServers("throughput", server)
    }

    suspend fun locateLatencyServers(server: Server? = null): List<Server> {
        return locateServers("latency", server)
    }

    private suspend fun locateServers(
        test: String,
        server: Server? = null,
    ): List<Server> {
        val locateUrl = this.locateUrl ?: locateUrls[msakServerEnv]
        val locatePath = when (test) {
            "throughput" -> LOCATE_THROUGHPUT_PATH
            "latency" -> LOCATE_LATENCY_PATH
            else -> throw InvalidParameterException("unknown test type $test")
        }

        // No locate URL for the given environment means we should use the local MSAK server.
        if (locateUrl == null) {
            val urls = when (test) {
                "throughput" -> {
                    val proto = "ws${if (msakLocalServerSecure) { "s" } else { "" }}"
                    mapOf(
                        "$proto:///$THROUGHPUT_DOWNLOAD_PATH" to "$proto://$msakLocalServerHost/$THROUGHPUT_DOWNLOAD_PATH",
                        "$proto:///$THROUGHPUT_UPLOAD_PATH" to "$proto://$msakLocalServerHost/$THROUGHPUT_UPLOAD_PATH",
                    )
                }
                "latency" -> {
                    val proto = "http${if (msakLocalServerSecure) { "s" } else { "" }}"
                    mapOf(
                        "$proto:///$LATENCY_AUTHORIZE_PATH" to "$proto://$msakLocalServerHost/$LATENCY_AUTHORIZE_PATH",
                        "$proto:///$LATENCY_RESULT_PATH" to "$proto://$msakLocalServerHost/$LATENCY_RESULT_PATH",
                    )
                }
                else -> throw InvalidParameterException("unknown test type $test")
            }

            return listOf(Server(msakLocalServerHost, null, urls))
        }

        // The site name is embedded in the server's machine (hostname), formatted as
        // "mlab<number>-<site>.<rest of hostname>".
        val site = if (server != null) {
            Regex("([^-.]+)\\.").find(server.machine)?.groupValues?.get(1) ?: throw Throwable("not site found in machine ${server.machine}")
        } else {
            null
        }

        return requestServers("$locateUrl$locatePath", site)
    }

    private suspend fun requestServers(
        locateUrl: String,
        site: String? = null,
    ): List<Server> = suspendCoroutine { continuation ->
        val request = Request.Builder()
            .url("${locateUrl}${if (site != null) { "?site=$site" } else { "" }}")
            .header("User-Agent", BuildConfig.USER_AGENT)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (response.code != 200 || body == null) {
                    Log.i(TAG, "locate request $request failed: $response")
                    continuation.resumeWithException(Throwable("locate request $request failed: $response"))
                    return
                }

                val results = try {
                    Gson().fromJson(body.charStream(), LocateResponse::class.java).results
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "locate response deserialization failed: $body", e)
                    continuation.resumeWithException(e)
                    return
                }

                continuation.resume(results)
            }
        })
    }
}
