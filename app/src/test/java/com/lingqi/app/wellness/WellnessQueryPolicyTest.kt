package com.lingqi.app.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessQueryPolicyTest {
    @Test
    fun acceptsOnlyFixedRecentPathsAndBoundedLimit() {
        assertEquals(
            WellnessQuery(WellnessSummaryKind.SLEEP, 7),
            WellnessQueryPolicy.parse(listOf("sleep", "recent"), mapOf("limit" to listOf("7")))
        )
        assertEquals(
            WellnessQuery(WellnessSummaryKind.MEDITATION, WellnessProviderContract.DEFAULT_LIMIT),
            WellnessQueryPolicy.parse(listOf("meditation", "recent"), emptyMap())
        )
        listOf("0", "31", "not-a-number").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                WellnessQueryPolicy.parse(listOf("sleep", "recent"), mapOf("limit" to listOf(invalid)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WellnessQueryPolicy.parse(listOf("sleep", "raw"), emptyMap())
        }
    }

    @Test
    fun rejectsClientControlledQueryShape() {
        assertThrows(IllegalArgumentException::class.java) {
            WellnessQueryPolicy.parse(listOf("sleep", "recent"), emptyMap(), projection = arrayOf("score"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WellnessQueryPolicy.parse(
                listOf("sleep", "recent"), emptyMap(), selection = "score > ?", selectionArgs = arrayOf("0")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WellnessQueryPolicy.parse(listOf("sleep", "recent"), emptyMap(), sortOrder = "score DESC")
        }
    }

    @Test
    fun publicColumnsDoNotExposeSensorDetails() {
        val published = (WellnessProviderContract.SLEEP_COLUMNS + WellnessProviderContract.MEDITATION_COLUMNS)
            .map(String::lowercase)
        listOf("epochs", "movement", "noise", "snore", "placement", "preferences", "checkpoint")
            .forEach { forbidden -> assertFalse(published.any { forbidden in it }) }
    }

    @Test
    fun callerRequiresPackageUidAndMatchingSignature() {
        val allowed = CallerIdentity(
            uidPackages = setOf(WellnessProviderContract.LINGLIAN_PACKAGE),
            reportedCallingPackage = WellnessProviderContract.LINGLIAN_PACKAGE,
            signaturesMatch = true
        )
        assertTrue(LinglianCallerAuthorization.isAllowed(allowed))
        assertFalse(LinglianCallerAuthorization.isAllowed(allowed.copy(reportedCallingPackage = "attacker.example")))
        assertFalse(LinglianCallerAuthorization.isAllowed(allowed.copy(uidPackages = setOf("attacker.example"))))
        assertFalse(LinglianCallerAuthorization.isAllowed(allowed.copy(signaturesMatch = false)))
    }
}
