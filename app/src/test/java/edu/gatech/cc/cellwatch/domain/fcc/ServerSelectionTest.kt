package edu.gatech.cc.cellwatch.domain.fcc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ServerSelectionTest {
    private val rtts = mapOf("slow" to 120.0, "fast" to 18.0, "medium" to 45.0)

    @Test
    fun picksTheServerWithTheLowestRtt() = runBlocking {
        assertEquals("fast", closestServer(listOf("slow", "fast", "medium")) { rtts.getValue(it) })
    }

    @Test
    fun aServerThatCannotBePingedRanksLast() = runBlocking {
        val failed = mutableListOf<String>()
        val chosen = closestServer(
            listOf("unreachable", "slow", "medium"),
            onError = { server, _ -> failed += server },
        ) {
            if (it == "unreachable") throw IOException("no route") else rtts.getValue(it)
        }
        assertEquals("medium", chosen)
        assertEquals(listOf("unreachable"), failed)
    }

    @Test
    fun fallsBackToTheFirstServerWhenNoneCanBePinged() = runBlocking {
        assertEquals("a", closestServer(listOf("a", "b")) { throw IOException("offline") })
    }
}
