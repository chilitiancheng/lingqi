package com.lingqi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpochPayloadCodecTest {
    @Test
    fun sqlCipherPayloadRoundTripsWithoutCallingLegacyKeystoreDecryptor() {
        var legacyCalls = 0
        val codec = EpochPayloadCodec {
            legacyCalls += 1
            error("legacy decryptor must not be used")
        }
        val json = "{\"movementRms\":0.25}"

        val decoded = codec.decode(codec.encode(json))

        assertEquals(json, decoded.json)
        assertFalse(decoded.requiresRewrite)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun legacyPayloadIsDecodedAndMarkedForTransactionalRewrite() {
        val codec = EpochPayloadCodec { encrypted ->
            assertEquals("legacy-ciphertext", encrypted)
            "{\"movementRms\":0.5}"
        }

        val decoded = codec.decode("legacy-ciphertext")

        assertEquals("{\"movementRms\":0.5}", decoded.json)
        assertTrue(decoded.requiresRewrite)
    }
}
