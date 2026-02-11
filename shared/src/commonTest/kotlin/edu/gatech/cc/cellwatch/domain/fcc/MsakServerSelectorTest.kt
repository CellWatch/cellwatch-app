package edu.gatech.cc.cellwatch.domain.fcc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MsakServerSelectorTest {
    @Test
    fun `chooseServers selects max ping throughput and falls back to first latency when no machine match`() = runBlocking {
            val throughputA = server("throughput-a")
            val throughputB = server("throughput-b")
            val latencyA = server("throughput-a")
            val latencyOther = server("latency-other")

            val locator = FakeLocator(
                throughput = listOf(throughputA, throughputB),
                latencyByMachine = mapOf(
                    "throughput-b" to listOf(latencyOther, latencyA),
                ),
            )
            val pinger = FakePinger(
                values = mapOf(
                    "throughput-a" to 20.0,
                    "throughput-b" to 100.0,
                )
            )

            val selected = MsakServerSelector.chooseServers(locator, pinger)
            assertEquals("throughput-b", selected.throughputServer.machine)
            assertEquals("latency-other", selected.latencyServer.machine)
        }

    @Test
    fun `chooseServers prefers matching-machine latency when present`() = runBlocking {
        val throughputA = server("throughput-a")
        val throughputB = server("throughput-b")
        val latencyMatch = server("throughput-b")
        val latencyOther = server("latency-other")

        val locator = FakeLocator(
            throughput = listOf(throughputA, throughputB),
            latencyByMachine = mapOf(
                "throughput-b" to listOf(latencyOther, latencyMatch),
            ),
        )
        val pinger = FakePinger(
            values = mapOf(
                "throughput-a" to 20.0,
                "throughput-b" to 100.0,
            )
        )

        val selected = MsakServerSelector.chooseServers(locator, pinger)
        assertEquals("throughput-b", selected.throughputServer.machine)
        assertEquals("throughput-b", selected.latencyServer.machine)
    }

    @Test
    fun `chooseServers falls back to first throughput when ping fails`() = runBlocking {
            val throughputA = server("throughput-a")
            val throughputB = server("throughput-b")
            val latency = server("throughput-a")

            val locator = FakeLocator(
                throughput = listOf(throughputA, throughputB),
                latencyByMachine = mapOf(
                    "throughput-a" to listOf(latency),
                ),
            )
            val pinger = FakePinger(
                throwsFor = setOf("throughput-a")
            )

            val selected = MsakServerSelector.chooseServers(locator, pinger)
            assertEquals("throughput-a", selected.throughputServer.machine)
            assertEquals("throughput-a", selected.latencyServer.machine)
        }

    @Test
    fun `chooseServers throws when throughput list is empty`() {
        runBlocking {
            val locator = FakeLocator(
                throughput = emptyList(),
                latencyByMachine = emptyMap(),
            )
            val pinger = FakePinger()

            assertFailsWith<IllegalStateException> {
                MsakServerSelector.chooseServers(locator, pinger)
            }
        }
    }

    @Test
    fun `chooseServers throws when latency list is empty`() {
        runBlocking {
            val throughput = server("throughput-a")
            val locator = FakeLocator(
                throughput = listOf(throughput),
                latencyByMachine = mapOf("throughput-a" to emptyList()),
            )
            val pinger = FakePinger(values = mapOf("throughput-a" to 50.0))

            assertFailsWith<IllegalStateException> {
                MsakServerSelector.chooseServers(locator, pinger)
            }
        }
    }

    @Test
    fun `chooseServers returns unreachable fallback pair on network unavailable`() = runBlocking {
            val locator = object : MsakServerLocator {
                override val fallbackHost: String = "locate.measurementlab.net"

                override suspend fun locateThroughputServers(): List<MsakServerEndpoint> {
                    throw MsakLocateNetworkUnavailableException("network down")
                }

                override suspend fun locateLatencyServers(
                    throughputServer: MsakServerEndpoint,
                ): List<MsakServerEndpoint> {
                    error("not expected")
                }
            }
            val pinger = FakePinger()

            val selected = MsakServerSelector.chooseServers(locator, pinger)

            assertEquals("locate.measurementlab.net", selected.throughputServer.machine)
            assertEquals("locate.measurementlab.net", selected.latencyServer.machine)
            assertEquals("ws://0.0.0.0", selected.throughputServer.urls["ws:///throughput/v1/download"])
            assertEquals("ws://0.0.0.0", selected.throughputServer.urls["http:///latency/v1/result"])
        }

    private fun server(machine: String): MsakServerEndpoint {
        return MsakServerEndpoint(
            machine = machine,
            urls = mapOf(
                "ws:///throughput/v1/download" to "ws://$machine/throughput/v1/download",
                "ws:///throughput/v1/upload" to "ws://$machine/throughput/v1/upload",
                "http:///latency/v1/authorize" to "http://$machine/latency/v1/authorize",
                "http:///latency/v1/result" to "http://$machine/latency/v1/result",
            ),
        )
    }

    private class FakeLocator(
        private val throughput: List<MsakServerEndpoint>,
        private val latencyByMachine: Map<String, List<MsakServerEndpoint>>,
    ) : MsakServerLocator {
        override val fallbackHost: String = "fallback-host"

        override suspend fun locateThroughputServers(): List<MsakServerEndpoint> = throughput

        override suspend fun locateLatencyServers(
            throughputServer: MsakServerEndpoint,
        ): List<MsakServerEndpoint> {
            return latencyByMachine[throughputServer.machine] ?: emptyList()
        }
    }

    private class FakePinger(
        private val values: Map<String, Double> = emptyMap(),
        private val throwsFor: Set<String> = emptySet(),
    ) : MsakHostPinger {
        override suspend fun pingMs(host: String): Double {
            if (throwsFor.contains(host)) {
                error("simulated ping failure")
            }
            return values[host] ?: 0.0
        }
    }
}
