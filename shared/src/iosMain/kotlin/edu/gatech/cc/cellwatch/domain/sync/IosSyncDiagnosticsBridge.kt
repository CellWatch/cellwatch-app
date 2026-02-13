package edu.gatech.cc.cellwatch.domain.sync

class IosSyncDiagnosticsBridge {
    fun configure(
        levelRaw: String?,
        maxSampledErrorsPerReport: Int,
        includeCauseChain: Boolean,
    ): String {
        val config = SyncDiagnosticsConfig(
            level = SyncDiagnosticsLevel.fromString(levelRaw),
            maxSampledErrorsPerReport = maxSampledErrorsPerReport,
            includeCauseChain = includeCauseChain,
        )
        SyncDiagnosticsRegistry.configure(config)
        return "level=${config.level}, maxSamples=${config.maxSampledErrorsPerReport}, includeCauseChain=${config.includeCauseChain}"
    }
}
