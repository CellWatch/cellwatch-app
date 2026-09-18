package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Asks an echo service for the device's public address and port.
 *
 * The FCC challenge process requires the device's source IP and port. Every
 * TcpTupleProvider in this module used to be a stub returning an RFC 5737
 * TEST-NET-3 documentation address (203.0.113.x), which was written straight
 * into the submission - and because that value was non-null, the Supabase
 * `fcc_submission_update_source_ip` trigger, which only fires on NULL, never
 * ran either. So every submission carried a placeholder.
 *
 * This restores what the pre-KMP app did (see frozenApp
 * `MeasurementRepository.getPubicTCPTuple`): a plain GET whose JSON body
 * deserialises into [TcpTuple].
 *
 * HTTP is done through [httpGetText], an expect/actual over each platform's own
 * stack, deliberately rather than by adding a Ktor client and per-target engines
 * to this module.
 */
class HttpTcpTupleProvider(
    private val serviceUrl: String,
    private val userAgent: String,
    private val nowMillis: () -> Long,
    private val httpGet: suspend (url: String, userAgent: String) -> String = ::httpGetText,
) : TcpTupleProvider {

    override suspend fun getPublicTcpTuple(): TcpTuple {
        val body = httpGet(serviceUrl, userAgent)
        val parsed = json.decodeFromString<TcpTupleResponse>(body)
        val address = parsed.remoteAddress?.trim()
        require(!address.isNullOrEmpty()) {
            "tcp tuple service returned no remoteAddress"
        }
        return TcpTuple(
            remoteAddress = address,
            remotePort = parsed.remotePort ?: 0,
            // The service's own timestamp when it provides one; otherwise ours,
            // so the submission always carries something sortable.
            timestamp = parsed.timestamp ?: nowMillis(),
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Wire shape of the echo service. Fields are optional so a partial or evolving
 * response is rejected explicitly by [HttpTcpTupleProvider] rather than failing
 * to deserialise.
 */
@Serializable
internal data class TcpTupleResponse(
    val remoteAddress: String? = null,
    val remotePort: Int? = null,
    val timestamp: Long? = null,
)

/** Plain GET returning the response body as text; throws on any non-2xx. */
internal expect suspend fun httpGetText(url: String, userAgent: String): String

/**
 * Chooses a provider for the configured echo service, or none.
 *
 * A blank or absent [serviceUrl] disables the lookup, which is the intended way
 * to switch it off while the service is down: submissions then leave
 * sourceIp/sourcePort null and Supabase's `fcc_submission_update_source_ip`
 * trigger records the address it observed instead.
 *
 * Note a lookup failure is already non-fatal - MeasurementSyncUseCase uploads
 * without a tuple rather than withholding the measurement - so leaving the URL
 * configured while the service is unreachable costs only the request timeout.
 */
fun tcpTupleProviderFor(
    serviceUrl: String?,
    userAgent: String,
    nowMillis: () -> Long,
): TcpTupleProvider =
    serviceUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { HttpTcpTupleProvider(it, userAgent, nowMillis) }
        ?: UnavailableTcpTupleProvider
