package com.lingqi.app.data

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class DecodedEpochPayload(
    val json: String,
    val requiresRewrite: Boolean
)

internal class EpochPayloadCodec(
    private val legacyDecrypt: (String) -> String
) {
    fun encode(json: String): String =
        SQLCIPHER_JSON_PREFIX + Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

    fun decode(stored: String): DecodedEpochPayload {
        if (stored.startsWith(SQLCIPHER_JSON_PREFIX)) {
            val json = String(
                Base64.getDecoder().decode(stored.removePrefix(SQLCIPHER_JSON_PREFIX)),
                StandardCharsets.UTF_8
            )
            return DecodedEpochPayload(json, requiresRewrite = false)
        }
        return DecodedEpochPayload(legacyDecrypt(stored), requiresRewrite = true)
    }

    private companion object {
        const val SQLCIPHER_JSON_PREFIX = "sqlcipher-json-v1:"
    }
}
