package edu.gatech.cc.cellwatch.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactSecretsTest {
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.c2lnbmF0dXJl"
    private val secret = "q2V9uMfakeSecret+/AbC0123456789xyz="
    private val deviceId = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"

    // Same shape as the message of a supabase-kt RestException.
    private val supabaseError = "canceling statement due to lock timeout\n" +
        "URL: https://example.supabase.co/rest/v1/rpc/insert_measurement\n" +
        "Headers: [Authorization=[Bearer $jwt], Prefer=[], apikey=[$jwt], " +
        "X-Device-ID=[$deviceId], X-Device-Secret=[$secret], Accept=[application/json]]\n" +
        "Http Method: POST"

    @Test
    fun removesSecretsFromSupabaseErrorMessages() {
        val redacted = redactSecrets(supabaseError)

        assertFalse(redacted.contains(secret))
        assertFalse(redacted.contains(jwt))
        assertTrue(redacted.contains("X-Device-Secret=[REDACTED]"))
        assertTrue(redacted.contains("Authorization=[REDACTED]"))
        assertTrue(redacted.contains("apikey=[REDACTED]"))
        // Details that help with debugging stay.
        assertTrue(redacted.contains("canceling statement due to lock timeout"))
        assertTrue(redacted.contains("X-Device-ID=[$deviceId]"))
        assertTrue(redacted.contains("Accept=[application/json]"))
    }

    @Test
    fun removesHeaderValuesWrittenWithColons() {
        val redacted = redactSecrets("x-device-secret: $secret, Authorization: Bearer $jwt")

        assertFalse(redacted.contains(secret))
        assertFalse(redacted.contains(jwt))
    }

    @Test
    fun leavesOrdinaryMessagesUnchanged() {
        val message = "network error uploading measurement $deviceId"

        assertEquals(message, redactSecrets(message))
    }

    @Test
    fun returnsTheSameExceptionWhenThereIsNothingToRedact() {
        val e = IllegalStateException("measurement already running")

        assertSame(e, redactSecrets(e))
    }

    @Test
    fun redactsTheWholeCauseChainAndKeepsStackTraces() {
        val cause = RuntimeException(supabaseError)
        val e = Exception("duplicate key error uploading measurement", cause)

        val redacted = redactSecrets(e)

        val messages = generateSequence(redacted) { it.cause }.map { it.message.orEmpty() }.toList()
        assertEquals(2, messages.size)
        assertTrue(messages.none { it.contains(secret) || it.contains(jwt) })
        assertTrue(messages[0].startsWith("java.lang.Exception: duplicate key error"))
        assertTrue(messages[1].startsWith("java.lang.RuntimeException: canceling statement"))
        assertEquals(e.stackTrace.toList(), redacted.stackTrace.toList())
        assertEquals(cause.stackTrace.toList(), redacted.cause!!.stackTrace.toList())
    }
}
