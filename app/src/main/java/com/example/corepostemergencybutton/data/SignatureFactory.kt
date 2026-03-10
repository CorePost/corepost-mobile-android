package com.example.corepostemergencybutton.data

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SignatureFactory {
    fun canonicalString(method: String, path: String, timestamp: String): String {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        return "${method.uppercase()}\n$normalizedPath\n$timestamp"
    }

    fun sign(secret: String, canonicalString: String): String {
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(canonicalString.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun buildHeaders(
        method: String,
        path: String,
        emergencyId: String,
        panicSecret: String,
        timestamp: String = (System.currentTimeMillis() / 1000L).toString(),
    ): Map<String, String> {
        val canonical = canonicalString(method = method, path = path, timestamp = timestamp)
        return mapOf(
            "X-EmergencyId" to emergencyId,
            "X-Timestamp" to timestamp,
            "X-Signature" to sign(secret = panicSecret, canonicalString = canonical),
        )
    }
}
