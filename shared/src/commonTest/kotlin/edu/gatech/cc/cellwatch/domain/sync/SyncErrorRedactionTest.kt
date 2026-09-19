package edu.gatech.cc.cellwatch.domain.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sync error samples are logged and shown in status text, so anything a
 * Supabase exception carries is effectively published.
 */
class SyncErrorRedactionTest {

    private val jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSJ9.aGfXuXAFqQUrB1jZKK_9QY5aYT8Y2p2WJpfb38"

    @Test
    fun `strips the header block carrying the device secret`() {
        val raw = """
            null value in column "timestamp" of relation "locations" violates not-null constraint
            URL: https://example.supabase.co/rest/v1/rpc/insert_measurement
            Headers: [Authorization=[Bearer $jwt], X-Device-ID=[abc], X-Device-Secret=[haB1bFotu5rfpwdM3k41NXhzedlG7rYc3mR/iw/JzqU=], Accept=[application/json]]
            Http Method: POST
        """.trimIndent()

        val redacted = raw.redactSecrets().orEmpty()

        assertFalse(redacted.contains("haB1bFotu5rfpwdM3k41NXhzedlG7rYc3mR"), "device secret survived redaction")
        assertFalse(redacted.contains(jwt), "bearer token survived redaction")
        // The diagnosable part has to survive, or redaction just hides the bug.
        assertTrue(redacted.contains("violates not-null constraint"))
        assertTrue(redacted.contains("insert_measurement"))
    }

    @Test
    fun `strips a bare token outside any header block`() {
        val redacted = "auth failed for token $jwt".redactSecrets().orEmpty()

        assertFalse(redacted.contains(jwt))
        assertTrue(redacted.contains("auth failed for token"))
    }

    @Test
    fun `leaves an ordinary message alone`() {
        val message = "connection refused"
        assertTrue(message.redactSecrets() == message)
    }
}
