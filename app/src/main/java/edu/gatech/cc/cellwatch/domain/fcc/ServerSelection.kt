package edu.gatech.cc.cellwatch.domain.fcc

import kotlinx.coroutines.CancellationException

/**
 * Picks the server with the lowest [rtt] (for example the average ping in milliseconds). A server
 * whose [rtt] throws ranks last and is reported to [onError]; if every one fails, the first server
 * (the locate API's own order) wins.
 */
internal suspend fun <T> closestServer(
    servers: List<T>,
    onError: (T, Throwable) -> Unit = { _, _ -> },
    rtt: suspend (T) -> Double,
): T = servers.minBy { server ->
    try {
        rtt(server)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        onError(server, t)
        Double.POSITIVE_INFINITY
    }
}
