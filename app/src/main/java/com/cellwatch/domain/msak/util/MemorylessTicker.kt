package com.cellwatch.domain.msak.util

import java.security.InvalidParameterException
import kotlin.concurrent.thread
import kotlin.math.ln
import kotlin.random.Random

// Copycat of github.com/m-lab/go/memoryless
class MemorylessTicker(
    private val expectedMillis: Long,
    private val maxMillis: Long,
    private val minMillis: Long,
) {
    init {
        if (maxMillis < expectedMillis || minMillis > expectedMillis) {
            throw InvalidParameterException("(minMillis <= expectedMillis <= maxMillis) must be true")
        }
    }

    private var runningThread: Thread? = null
    private var stop = true

    fun start(callback: () -> Unit) {
        stop = false
        runningThread = thread {
            while (!stop) {
                Thread.sleep(nextDelayMillis())
                if (!stop) callback()
            }
        }
    }

    fun stop() {
        stop = true
        runningThread = null
    }

    // Generate a random variable with an exponential distribution, based on
    // https://en.wikipedia.org/wiki/Exponential_distribution#Random_variate_generation.
    private fun randExp(): Double {
        val u = Random.nextDouble()
        return -ln(1 - u)
    }

    private fun nextDelayMillis(): Long {
        val delay = (randExp() * expectedMillis).toLong()
        if (delay > maxMillis) {
            return maxMillis
        }

        if (delay < minMillis) {
            return minMillis
        }

        return delay
    }
}