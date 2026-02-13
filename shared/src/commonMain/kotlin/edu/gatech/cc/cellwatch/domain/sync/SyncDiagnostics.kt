package edu.gatech.cc.cellwatch.domain.sync

enum class SyncDiagnosticsLevel {
    OFF,
    BASIC,
    VERBOSE,
    ;

    companion object {
        fun fromString(raw: String?): SyncDiagnosticsLevel {
            return when (raw?.trim()?.uppercase()) {
                "OFF" -> OFF
                "VERBOSE" -> VERBOSE
                else -> BASIC
            }
        }
    }
}

data class SyncDiagnosticsConfig(
    val level: SyncDiagnosticsLevel = SyncDiagnosticsLevel.BASIC,
    val maxSampledErrorsPerReport: Int = 6,
    val includeCauseChain: Boolean = false,
)

enum class SyncErrorCategory {
    NETWORK,
    DUPLICATE_NOT_MARKED,
    BLOCKED,
    UNEXPECTED,
}

data class SyncErrorSample(
    val category: SyncErrorCategory,
    val contextId: String? = null,
    val exceptionType: String,
    val message: String? = null,
    val rootCauseType: String? = null,
    val rootCauseMessage: String? = null,
    val upstreamChain: List<String> = emptyList(),
)

data class SyncErrorSummary(
    val totalErrors: Int = 0,
    val sampledErrors: List<SyncErrorSample> = emptyList(),
) {
    val droppedErrors: Int
        get() = (totalErrors - sampledErrors.size).coerceAtLeast(0)
}

object SyncDiagnosticsRegistry {
    @Volatile
    private var config: SyncDiagnosticsConfig = SyncDiagnosticsConfig()

    fun configure(config: SyncDiagnosticsConfig) {
        this.config = config
    }

    fun current(): SyncDiagnosticsConfig = config
}

internal fun SyncErrorSummary.recordError(
    category: SyncErrorCategory,
    throwable: Throwable,
    contextId: String?,
    config: SyncDiagnosticsConfig = SyncDiagnosticsRegistry.current(),
): SyncErrorSummary {
    val nextTotal = totalErrors + 1
    if (config.level == SyncDiagnosticsLevel.OFF) {
        return copy(totalErrors = nextTotal)
    }
    if (sampledErrors.size >= config.maxSampledErrorsPerReport.coerceAtLeast(0)) {
        return copy(totalErrors = nextTotal)
    }
    val sample = throwable.toSyncErrorSample(
        category = category,
        contextId = contextId,
        config = config,
    )
    return copy(
        totalErrors = nextTotal,
        sampledErrors = sampledErrors + sample,
    )
}

private fun Throwable.toSyncErrorSample(
    category: SyncErrorCategory,
    contextId: String?,
    config: SyncDiagnosticsConfig,
): SyncErrorSample {
    val root = generateSequence(this as Throwable?) { it.cause }.last()
    val includeChain = config.includeCauseChain || config.level == SyncDiagnosticsLevel.VERBOSE
    val chain = if (includeChain) {
        generateSequence(this as Throwable?) { it.cause }
            .mapNotNull { throwable ->
                val type = throwable::class.qualifiedName ?: throwable::class.simpleName
                val message = throwable.message
                if (message.isNullOrBlank()) type else "$type: $message"
            }
            .toList()
    } else {
        emptyList()
    }
    return SyncErrorSample(
        category = category,
        contextId = contextId,
        exceptionType = this::class.qualifiedName ?: this::class.simpleName.orEmpty(),
        message = message,
        rootCauseType = root::class.qualifiedName ?: root::class.simpleName,
        rootCauseMessage = root.message,
        upstreamChain = chain,
    )
}

fun SyncErrorSummary.renderForStatus(): String {
    if (totalErrors == 0) return "errors=none"
    val header = "errors(total=$totalErrors,sampled=${sampledErrors.size},dropped=$droppedErrors)"
    if (sampledErrors.isEmpty()) return header
    val renderedSamples = sampledErrors.joinToString(separator = " | ") { sample ->
        val root = if (sample.rootCauseType.isNullOrBlank()) {
            ""
        } else {
            ", root=${sample.rootCauseType}:${sample.rootCauseMessage ?: "n/a"}"
        }
        val chain = if (sample.upstreamChain.isEmpty()) {
            ""
        } else {
            ", chain=${sample.upstreamChain.joinToString(" -> ")}"
        }
        "[${
            sample.category
        } ctx=${sample.contextId ?: "n/a"} type=${sample.exceptionType}: ${sample.message ?: "n/a"}$root$chain]"
    }
    return "$header $renderedSamples"
}
