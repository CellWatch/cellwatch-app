package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_wired
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * What interface the system would actually use right now.
 *
 * CellWatch measures cellular performance, and the FCC challenge process only
 * accepts measurements taken over cellular - so this has to be observed, not
 * assumed. iOS offers no way to force traffic onto cellular for a URLSession or
 * a BSD socket (only Network.framework connections can pin an interface, via
 * NWParameters.requiredInterfaceType, and MSAK uses neither), so detecting and
 * reporting honestly is the only correct behaviour.
 */
internal data class IosNetworkPath(
    val satisfied: Boolean,
    val usesCellular: Boolean,
    val usesWifi: Boolean,
    val usesWired: Boolean,
) {
    /**
     * Note that WIRED covers USB tethering, where the phone routes through a
     * connected computer. That is not cellular and must not be reported as such.
     */
    val connectionType: NetworkConnectionType
        get() = when {
            !satisfied -> NetworkConnectionType.NONE
            usesCellular -> NetworkConnectionType.CELLULAR
            usesWifi || usesWired -> NetworkConnectionType.WIFI
            else -> NetworkConnectionType.NONE
        }

    val describedInterface: String
        get() = when {
            !satisfied -> "unsatisfied"
            usesCellular -> "cellular"
            usesWifi -> "wifi"
            usesWired -> "wired/tethered"
            else -> "other"
        }
}

/**
 * Reads the current network path, or null if it cannot be determined in time.
 *
 * nw_path_monitor delivers asynchronously on a dispatch queue, so this waits for
 * the first update. Callers must treat null as "unknown" rather than as "not
 * cellular": the distinction decides whether a measurement is submittable.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun readIosNetworkPath(
    timeoutMs: Long = 2_000,
): IosNetworkPath? = withTimeoutOrNull(timeoutMs.milliseconds) {
    val monitor = nw_path_monitor_create()
    try {
        suspendCancellableCoroutine { cont ->
            // The handler fires on every path change; only the first resumes.
            val resumed = atomic(false)
            nw_path_monitor_set_update_handler(monitor) { path ->
                if (resumed.compareAndSet(expect = false, update = true)) {
                    cont.resume(
                        IosNetworkPath(
                            satisfied = nw_path_get_status(path) == nw_path_status_satisfied,
                            usesCellular = nw_path_uses_interface_type(path, nw_interface_type_cellular),
                            usesWifi = nw_path_uses_interface_type(path, nw_interface_type_wifi),
                            usesWired = nw_path_uses_interface_type(path, nw_interface_type_wired),
                        )
                    )
                }
            }
            nw_path_monitor_set_queue(
                monitor,
                dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0uL),
            )
            nw_path_monitor_start(monitor)
        }
    } finally {
        nw_path_monitor_cancel(monitor)
    }
}
