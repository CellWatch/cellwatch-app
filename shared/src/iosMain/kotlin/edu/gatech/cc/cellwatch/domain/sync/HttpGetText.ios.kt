package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun httpGetText(url: String, userAgent: String): String =
    suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL(string = url)
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.setValue(userAgent, forHTTPHeaderField = "User-Agent")
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                cont.resumeWithException(Exception("tcp tuple request failed: ${error.localizedDescription}"))
                return@dataTaskWithRequest
            }
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: -1
            if (status !in 200..299) {
                cont.resumeWithException(Exception("tcp tuple request returned HTTP $status"))
                return@dataTaskWithRequest
            }
            val body = data?.let {
                NSString.create(data = it, encoding = NSUTF8StringEncoding) as String?
            }
            if (body == null) {
                cont.resumeWithException(Exception("tcp tuple request returned no body"))
            } else {
                cont.resume(body)
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
