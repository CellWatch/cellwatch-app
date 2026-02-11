package edu.gatech.cc.cellwatch.data.sync

enum class SyncTransportTarget {
    LOCAL,
    REMOTE,
}

interface SyncSupabaseConfigResolver {
    fun resolve(target: SyncTransportTarget = SyncTransportTarget.LOCAL): SyncSupabaseConfig
}
