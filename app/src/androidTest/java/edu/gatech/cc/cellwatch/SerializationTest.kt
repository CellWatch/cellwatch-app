package edu.gatech.cc.cellwatch

import edu.gatech.cc.cellwatch.core.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SerializationTest {

    private val measurementJsonString = """
            {
              "id": "ada9e884-f829-497e-9a67-be8a9e22ded1",
              "group_id": "a95f7bba-634b-4f2f-925a-20117c79b108",
              "campaign_id": "b53b5c0e-2e4d-46fd-8616-95055c29b1d6",
              "session_id": "fbb5572b-c5f9-46d7-ac41-0f367a3f61d7",
              "device_id": "c52856fa-32af-4cdd-b370-25e9f954b3ce",
              "device_manufacturer": "Google",
              "device_model": "Pixel 5",
              "device_os_name": "Android",
              "device_os_version": "13",
              "app_name": "CellWatch",
              "provider": "T-Mobile",
              "type": "upload",
              "timestamp": "2023-07-12T13:19:21.196+00:00",
              "duration": 8874314,
              "scheduled": false,
              "success": true,
              "carrier_aggregation": false,
              "network_connected": true,
              "network_available": true,
              "network_roaming": false,
              "extra_data": "extraData",
              "created_on": "2023-07-12T13:19:23.078772+00:00",
              "updated_on": "2023-07-12T13:19:23.078772+00:00",
              "locations": [
                {
                  "id": "67bb3d80-08c5-4439-8ff7-faa1a513f0d4",
                  "timestamp": "2023-07-12T13:19:21.183+00:00",
                  "lat": 33.297,
                  "lon": -84.13,
                  "accuracy": 20.45883,
                  "speed": 0.1,
                  "speed_accuracy": null,
                  "heading": 234.44,
                  "measurement_id": "ada9e884-f829-497e-9a67-be8a9e22ded1",
                  "created_on": "2023-07-12T13:19:23.078772+00:00",
                  "updated_on": "2023-07-12T13:19:23.078772+00:00"
                },
                {
                  "id": "230d347e-1325-49ff-bedd-fb1937337a00",
                  "timestamp": "2023-07-12T13:19:21.184+00:00",
                  "lat": 33.4797,
                  "lon": -84.5111,
                  "accuracy": 20.93,
                  "speed": 0.23,
                  "speed_accuracy": null,
                  "heading": 233.3,
                  "measurement_id": "ada9e884-f829-497e-9a67-be8a9e22ded1",
                  "created_on": "2023-07-12T13:19:23.078772+00:00",
                  "updated_on": "2023-07-12T13:19:23.078772+00:00"
                }
              ],
              "cells": [
                {
                  "id": "c0647b23-fed0-4c57-bffc-d99bdf6e0029",
                  "timestamp": "2023-07-12T13:19:21.187+00:00",
                  "cell_id": 234,
                  "physical_cell_id": 4321,
                  "cell_connection": 1,
                  "network_generation": "5G",
                  "network_subtype": "GSM",
                  "signal_strength": -103,
                  "rssi": -78,
                  "rsrp": -102,
                  "rsrq": -12,
                  "sinr": 2,
                  "csi_rsrp": -101,
                  "csi_rsrq": -13,
                  "csi_sinr": 2,
                  "cqi": 4,
                  "spectrum_band": "n41",
                  "spectrum_bandwidth": 100,
                  "arfcn": 528000,
                  "measurement_id": "ada9e884-f829-497e-9a67-be8a9e22ded1",
                  "created_on": "2023-07-12T13:19:23.078772+00:00",
                  "updated_on": "2023-07-12T13:19:23.078772+00:00"
                },
                {
                  "id": "260f227c-a125-48f2-8440-6f36177e57a8",
                  "timestamp": "2023-07-12T13:19:21.187+00:00",
                  "cell_id": 235,
                  "physical_cell_id": 4322,
                  "cell_connection": 1,
                  "network_generation": "5G",
                  "network_subtype": "GSM",
                  "signal_strength": -102,
                  "rssi": -79,
                  "rsrp": -101,
                  "rsrq": -11,
                  "sinr": 2,
                  "csi_rsrp": -100,
                  "csi_rsrq": -14,
                  "csi_sinr": 2,
                  "cqi": 4,
                  "spectrum_band": "n41",
                  "spectrum_bandwidth": 100,
                  "arfcn": 528000,
                  "measurement_id": "ada9e884-f829-497e-9a67-be8a9e22ded1",
                  "created_on": "2023-07-12T13:19:23.078772+00:00",
                  "updated_on": "2023-07-12T13:19:23.078772+00:00"
                }
              ],
              "latency_data": null,
              "upload_download_data": {
                "id": "c8040356-31ea-470b-997a-ff1a08c33e25",
                "measurement_id": "ada9e884-f829-497e-9a67-be8a9e22ded1",
                "warmup_duration": 10234,
                "warmup_bytes": 134425,
                "duration": 9372444,
                "bytes": 83724,
                "servers": [
                  "server1",
                  "server2"
                ],
                "created_on": "2023-07-12T13:19:23.078772+00:00",
                "updated_on": "2023-07-12T13:19:23.078772+00:00"
              }
            }
    """.trimIndent()

    companion object {
        private const val TAG = "SerializationTest"
    }

    @Test
    @Throws(Exception::class)
    fun deserializeMeasurement() {
        val measurement: NetworkMeasurement = Json.decodeFromString(measurementJsonString)
        Log.d(TAG, "Decoded measurement: $measurement")
    }
}
