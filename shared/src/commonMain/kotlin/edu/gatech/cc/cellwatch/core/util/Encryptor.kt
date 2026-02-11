//package edu.gatech.cc.cellwatch.core.util
//
//import dev.whyoleg.cryptography.CryptographyProvider
//import dev.whyoleg.cryptography.algorithms.AES
//import edu.gatech.cc.cellwatch.core.util.CryptographyInit
//
///**
// * KMP-safe encryption facade.
// *
// * Crypto
// * - AES-256-GCM (AEAD)
// *
// * Encoding
// * - Output is Base64 (no newlines) of the provider-produced ciphertext bytes.
// *   The ciphertext bytes include everything required for decryption
// *   (nonce + ciphertext + tag).
// *
// * Key management
// * - Generates a process-local key on first use.
// * - Persist the encoded key if cross-restart decryption is required.
// */
//object Encryptor {
//
//    init {
//        CryptographyInit.ensureInstalled()
//    }
//
//    private val provider by lazy { CryptographyProvider.Default }
//    private val aesGcm by lazy { provider.get(AES.GCM) }
//
//    private val key: AES.GCM.Key by lazy {
//        aesGcm
//            .keyGenerator(keySize = AES.Key.Size.B256)
//            .generateKeyBlocking()
//    }
//
//    private val cipher by lazy { key.cipher() }
//
//    fun encrypt(plaintext: String): String {
//        val pt = plaintext.encodeToByteArray()
//        val ct: ByteArray = cipher.encryptBlocking(pt)
//        return Base64.encode(ct)
//    }
//
//    fun decrypt(ciphertext: String): String {
//        val ct = Base64.decode(ciphertext)
//        val pt: ByteArray = cipher.decryptBlocking(ct)
//        return pt.decodeToString()
//    }
//
//    /**
//     * Minimal Base64 implementation (RFC 4648) for KMP commonMain.
//     */
//    private object Base64 {
//        private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
//        private val inverse = IntArray(256) { -1 }.apply {
//            for (i in alphabet.indices) this[alphabet[i].code] = i
//            this['='.code] = -2
//        }
//
//        fun encode(data: ByteArray): String {
//            if (data.isEmpty()) return ""
//            val out = CharArray(((data.size + 2) / 3) * 4)
//
//            var i = 0
//            var o = 0
//            while (i < data.size) {
//                val b0 = data[i++].toInt() and 0xFF
//                val b1 = if (i < data.size) data[i++].toInt() and 0xFF else -1
//                val b2 = if (i < data.size) data[i++].toInt() and 0xFF else -1
//
//                val trip = (b0 shl 16) or ((if (b1 >= 0) b1 else 0) shl 8) or (if (b2 >= 0) b2 else 0)
//
//                out[o++] = alphabet[(trip ushr 18) and 0x3F]
//                out[o++] = alphabet[(trip ushr 12) and 0x3F]
//                out[o++] = if (b1 >= 0) alphabet[(trip ushr 6) and 0x3F] else '='
//                out[o++] = if (b2 >= 0) alphabet[trip and 0x3F] else '='
//            }
//            return out.concatToString()
//        }
//
//        fun decode(s: String): ByteArray {
//            val cleaned = s.filterNot { it.isWhitespace() }
//            if (cleaned.isEmpty()) return ByteArray(0)
//            if (cleaned.length % 4 != 0) throw IllegalArgumentException("Invalid base64 length")
//
//            var padding = 0
//            if (cleaned.endsWith("==")) padding = 2
//            else if (cleaned.endsWith("=")) padding = 1
//
//            val out = ByteArray((cleaned.length / 4) * 3 - padding)
//
//            var i = 0
//            var o = 0
//            while (i < cleaned.length) {
//                val v0 = inv(cleaned[i++].code)
//                val v1 = inv(cleaned[i++].code)
//                val v2 = inv(cleaned[i++].code)
//                val v3 = inv(cleaned[i++].code)
//
//                val quad = (v0 shl 18) or (v1 shl 12) or ((v2 and 0x3F) shl 6) or (v3 and 0x3F)
//
//                if (o < out.size) out[o++] = ((quad ushr 16) and 0xFF).toByte()
//                if (o < out.size && v2 != -2) out[o++] = ((quad ushr 8) and 0xFF).toByte()
//                if (o < out.size && v3 != -2) out[o++] = (quad and 0xFF).toByte()
//            }
//
//            return out
//        }
//
//        private fun inv(code: Int): Int {
//            val v = inverse.getOrNull(code) ?: -1
//            if (v == -1) throw IllegalArgumentException("Invalid base64 character")
//            return v
//        }
//    }
//}