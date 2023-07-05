package com.cellwatch.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import java.util.UUID

@Entity
data class CellEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),
    val timestamp: Instant? = null,

    val cellId: Long? = null,
    val physicalCellId: Long? = null,
    val cellConnection: Int? = null,
    val networkGeneration: String? = null,
    val networkSubtype: String? = null,
    val signalStrength: Int? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Int? = null,
    val cqi: Int? = null,
    val spectrumBand: String? = null,
    val spectrumBandwidth: Float? = null,
    val arfcn: Int? = null,

    var measurementId: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

//id                  uuid default uuid_generate_v4() primary key,
//timestamp           timestamptz default now() not null,
//cell_id             integer,
//physical_cell_id    integer,
//cell_connection     integer, -- 0 | 1 | 2 // 0: not serving, 1: primary serving, 2: secondary serving
//network_generation  varchar(16),
//network_subtype     integer,
//signal_strength     integer,
//rssi                integer,
//rsrp                integer,
//rsrq                integer,
//sinr                integer,
//csi_rsrp            integer,
//csi_rsrq            integer,
//csi_sinr            integer,
//cqi                 integer,
//spectrum_band       text,
//spectrum_bandwidth  integer,
//arfcn               integer,
