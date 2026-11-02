package edu.gatech.cc.cellwatch.data.model

/**
 * Field names from the FCC's "Broadband Data Collection: Data Specifications for Mobile Speed Test
 * Data" (July 28, 2025), section 5.1, "Hierarchical Mobile Speed Test Data (JSON)". Each set is
 * one of the section's tables; the section number is beside it.
 */
internal object BdcSpec {
    /** §5.1: the file itself. */
    val FILE = setOf("submission_category", "contact", "submissions")

    /** §5.1.1 Contact Object. */
    val CONTACT = setOf("name", "email", "phone")

    /** §5.1.2 Submission Object. */
    val SUBMISSION = setOf(
        "test_id",
        "device_timestamp",
        "server_timestamp",
        "server_source_ip_address",
        "server_source_port",
        "device_imei",
        "device_type",
        "manufacturer",
        "model",
        "operating_system",
        "device_tac",
        "device_id",
        "app_name",
        "app_version",
        "provider_name",
        "sim_mobile_country_code",
        "sim_mobile_network_code",
        "net_mobile_country_code",
        "net_mobile_network_code",
        "in_vehicle_flag",
        "environment_code",
        "external_antenna_flag",
        "scheduled_test_flag",
        "tests",
    )

    /** §5.1.3 Test Object. */
    val TEST = setOf("download", "upload", "latency")

    /** §5.1.4 Download Test Object; §5.1.5 Upload Test Object has the same fields. */
    val THROUGHPUT_TEST = setOf(
        "timestamp",
        "warmup_duration",
        "warmup_bytes_transferred",
        "duration",
        "bytes_transferred",
        "bytes_sec",
        "locations",
        "cells",
        "targets",
        "connection_type",
        "success_flag",
        "carrier_aggregation_flag",
        "network_connected_flag",
        "network_available_flag",
        "network_roaming_flag",
    )

    /** §5.1.6 Latency Test Object. */
    val LATENCY_TEST = setOf(
        "timestamp",
        "duration",
        "round_trip_time",
        "jitter",
        "packets_sent",
        "packets_received",
        "locations",
        "cells",
        "targets",
        "connection_type",
        "success_flag",
        "carrier_aggregation_flag",
        "network_connected_flag",
        "network_available_flag",
        "network_roaming_flag",
    )

    /** §5.1.7 Location Objects. */
    val LOCATION = setOf("timestamp", "latitude", "longitude", "horizontal_accuracy", "speed", "speed_accuracy")

    /** §5.1.8.1 Cell Objects (Standard), the format for BDC data (§5.1.8.2 is for USF data). */
    val CELL = setOf(
        "timestamp",
        "cell_id",
        "physical_cell_id",
        "cell_connection",
        "network_generation",
        "network_subtype",
        "signal_strength",
        "rssi",
        "rsrp",
        "rsrq",
        "sinr",
        "csi_rsrp",
        "csi_rsrq",
        "csi_sinr",
        "cqi",
        "spectrum_band",
        "spectrum_bandwidth",
        "arfcn",
    )
}
