package com.example.watchtest.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    private val allGranted = mapOf(Perm.HEART to true, Perm.ACTIVITY to true, Perm.NOTIFICATIONS to true)
    private fun paired(vararg overrides: Pair<Perm, Boolean>) =
        StatusInput(paired = true, permissions = allGranted + overrides.toMap(), fallSupported = true)

    @Test fun healthyPairedWatchIsOkWithItsHeartRate() {
        assertEquals(HomeState.Ok(72), homeState(paired().copy(heartRate = 72)))
        assertEquals(HomeState.Ok(null), homeState(paired()))
    }

    @Test fun neverPairedAndRefusedAreDifferentStates() {
        assertEquals(HomeState.Unpaired, homeState(StatusInput()))
        assertEquals(HomeState.Refused, homeState(StatusInput(refused = true)))
    }

    @Test fun aPairedWatchIsNeverShownAsRefused() {
        assertEquals(HomeState.Ok(null), homeState(paired().copy(refused = true)))
    }

    @Test fun eachMissingPermissionIsNamedInOrder() {
        assertEquals(HomeState.Attention(listOf(Perm.ACTIVITY)), homeState(paired(Perm.ACTIVITY to false)))
        assertEquals(
            HomeState.Attention(listOf(Perm.HEART, Perm.NOTIFICATIONS)),
            homeState(paired(Perm.NOTIFICATIONS to false, Perm.HEART to false)),
        )
    }

    @Test fun aRevokedHealthPermissionNeedsAttentionEvenIfCheckboxesLookGranted() {
        assertEquals(HomeState.Attention(listOf(Perm.HEART)), homeState(paired().copy(permissionLost = true)))
    }

    @Test fun missingPermissionBeatsWaitingBecauseItCanBeFixed() {
        val i = paired(Perm.ACTIVITY to false).copy(queued = 4, uploadBlocked = true)
        assertTrue(homeState(i) is HomeState.Attention)
    }

    @Test fun queuedItemsOnlyMeanWaitingWhenTheUploadIsFailing() {
        assertEquals(HomeState.Waiting(3), homeState(paired().copy(queued = 3, uploadBlocked = true)))
        // A held-back fall or a fresh batch is queued for a moment while everything is fine.
        assertEquals(HomeState.Ok(null), homeState(paired().copy(queued = 3, uploadBlocked = false)))
        assertEquals(HomeState.Ok(null), homeState(paired().copy(queued = 0, uploadBlocked = true)))
    }

    @Test fun queuedItemsWithNoConnectionMeanWaitingEvenBeforeAnyAttemptFailed() {
        // Work is held back by the system while offline, so no upload has failed yet.
        val offline = paired().copy(queued = 2, uploadBlocked = false, networkAvailable = false)
        assertEquals(HomeState.Waiting(2), homeState(offline))
        assertEquals(HomeState.Ok(null), homeState(offline.copy(queued = 0)))
        assertFalse(detailRows(offline).first { it.key == DetailKey.NETWORK }.ok)
    }

    @Test fun heartRateIsShownOnlyWhileFresh() {
        assertEquals(72, freshHeartRate(72.4, 10_000))
        assertEquals(73, freshHeartRate(72.6, 119_999))
        assertEquals(72, freshHeartRate(72.0, HEART_RATE_FRESH_MS))
        assertNull(freshHeartRate(72.0, HEART_RATE_FRESH_MS + 1))
        assertNull(freshHeartRate(null, 0))
        assertNull(freshHeartRate(Double.NaN, 0))
        assertNull(freshHeartRate(72.0, -5))
    }

    @Test fun detailRowsFlagEveryProblem() {
        val ok = detailRows(paired())
        assertEquals(5, ok.size)
        assertTrue(ok.all { it.ok })

        val bad = detailRows(paired(Perm.ACTIVITY to false).copy(fallSupported = false, queued = 2, uploadBlocked = true, permissionLost = true))
        val byKey = bad.associate { it.key to it.ok }
        assertFalse(byKey.getValue(DetailKey.HEART))
        assertFalse(byKey.getValue(DetailKey.ACTIVITY))
        assertTrue(byKey.getValue(DetailKey.NOTIFICATIONS))
        assertFalse(byKey.getValue(DetailKey.FALL))
        assertFalse(byKey.getValue(DetailKey.NETWORK))
    }

    @Test fun unknownFallSupportIsNotReportedAsAvailable() {
        val row = detailRows(paired().copy(fallSupported = null)).first { it.key == DetailKey.FALL }
        assertFalse(row.ok)
    }
}
