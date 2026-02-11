package edu.gatech.cc.cellwatch.core.util

/**
 * KMP-safe contact validation utilities.
 *
 * Notes:
 * - Phone validation is intentionally conservative and US-centric.
 * - Email validation uses a pragmatic regex (not a full RFC 5322 parser).
 */
object ContactInfoValidator {

    // Accepts either "##########" or "###-###-####".
    // Returns normalized "###-###-####" or null.
    fun asValidPhoneNumber(phone: String): String? {
        val trimmed = phone.trim()

        return when {
            trimmed.matches(Regex("^[0-9]{3}-[0-9]{3}-[0-9]{4}$")) -> trimmed
            trimmed.matches(Regex("^[0-9]{10}$")) ->
                "${trimmed.substring(0, 3)}-${trimmed.substring(3, 6)}-${trimmed.substring(6)}"
            else -> null
        }
    }

    /**
     * Returns the trimmed email if it is plausibly valid; otherwise null.
     *
     * This is designed for UI-level validation, not authoritative deliverability.
     */
    fun asValidEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return null

        // Basic structural guards.
        if (trimmed.length > 254) return null
        if (trimmed.contains(' ')) return null
        val at = trimmed.indexOf('@')
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.lastIndex) return null

        // Pragmatic regex: local-part and domain with at least one dot TLD-like segment.
        // - local: letters/digits plus common punctuation
        // - domain labels: letters/digits/hyphen, separated by dots
        val emailRegex = Regex(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@" +
                "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
                "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
        )

        return if (emailRegex.matches(trimmed)) trimmed else null
    }
}