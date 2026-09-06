package com.alliehe.core.data

import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G5 / G3 effective-mode state machine (pure model transitions used by UserPrefsRepository).
 */
class PerformanceModeLogicTest {

    @Test
    fun reduceMotion_forcesPowerSaverEffectiveMode() {
        val prefs = UserPrefs(
            performanceMode = PerformanceMode.Performance,
            lastNonSaverMode = PerformanceMode.Performance,
            reduceMotion = true,
        )
        assertEquals(PerformanceMode.PowerSaver, prefs.effectivePerformanceMode)
    }

    @Test
    fun withoutReduceMotion_usesStoredPerformanceMode() {
        val prefs = UserPrefs(
            performanceMode = PerformanceMode.Balanced,
            reduceMotion = false,
        )
        assertEquals(PerformanceMode.Balanced, prefs.effectivePerformanceMode)
    }

    @Test
    fun powerSaverOn_remembersCurrentNonSaverTier() {
        val before = UserPrefs(
            performanceMode = PerformanceMode.Performance,
            lastNonSaverMode = PerformanceMode.Balanced,
        )
        val after = before.withPowerSaverEnabled(true)
        assertEquals(PerformanceMode.PowerSaver, after.performanceMode)
        assertEquals(PerformanceMode.Performance, after.lastNonSaverMode)
        assertEquals(PerformanceMode.PowerSaver, after.effectivePerformanceMode)
    }

    @Test
    fun powerSaverOff_restoresLastNonSaverMode() {
        val before = UserPrefs(
            performanceMode = PerformanceMode.PowerSaver,
            lastNonSaverMode = PerformanceMode.Balanced,
            reduceMotion = false,
        )
        val after = before.withPowerSaverEnabled(false)
        assertEquals(PerformanceMode.Balanced, after.performanceMode)
        assertEquals(PerformanceMode.Balanced, after.effectivePerformanceMode)
        assertEquals(PerformanceMode.Balanced, after.lastNonSaverMode)
    }

    @Test
    fun powerSaverOff_doesNotDefaultToPerformance() {
        val after = UserPrefs(
            performanceMode = PerformanceMode.PowerSaver,
            lastNonSaverMode = PerformanceMode.Balanced,
        ).withPowerSaverEnabled(false)
        assertEquals(PerformanceMode.Balanced, after.performanceMode)
    }

    @Test
    fun powerSaverOnWhileAlreadySaver_keepsExistingLastNonSaver() {
        val before = UserPrefs(
            performanceMode = PerformanceMode.PowerSaver,
            lastNonSaverMode = PerformanceMode.Performance,
        )
        val after = before.withPowerSaverEnabled(true)
        assertEquals(PerformanceMode.PowerSaver, after.performanceMode)
        assertEquals(PerformanceMode.Performance, after.lastNonSaverMode)
    }

    @Test
    fun enablingReduceMotion_forcesPowerSaverAndRemembersTier() {
        val before = UserPrefs(
            performanceMode = PerformanceMode.Performance,
            lastNonSaverMode = PerformanceMode.Balanced,
            reduceMotion = false,
        )
        val after = before.withReduceMotion(true)
        assertTrue(after.reduceMotion)
        assertEquals(PerformanceMode.PowerSaver, after.performanceMode)
        assertEquals(PerformanceMode.Performance, after.lastNonSaverMode)
        assertEquals(PerformanceMode.PowerSaver, after.effectivePerformanceMode)
    }

    @Test
    fun closingReduceMotion_restoresLastNonSaverMode() {
        val before = UserPrefs(
            performanceMode = PerformanceMode.PowerSaver,
            lastNonSaverMode = PerformanceMode.Performance,
            reduceMotion = true,
        )
        val after = before.withReduceMotion(false)
        assertFalse(after.reduceMotion)
        assertEquals(PerformanceMode.Performance, after.performanceMode)
        assertEquals(PerformanceMode.Performance, after.effectivePerformanceMode)
    }

    @Test
    fun reduceMotionOn_overridesStoredPerformanceEvenIfBalanced() {
        val prefs = UserPrefs(
            performanceMode = PerformanceMode.Balanced,
            reduceMotion = true,
        )
        assertEquals(PerformanceMode.PowerSaver, prefs.effectivePerformanceMode)
    }

    @Test
    fun setPerformanceMode_toNonSaver_updatesLastNonSaver() {
        val after = UserPrefs(
            performanceMode = PerformanceMode.Balanced,
            lastNonSaverMode = PerformanceMode.Balanced,
        ).withPerformanceMode(PerformanceMode.Performance)
        assertEquals(PerformanceMode.Performance, after.performanceMode)
        assertEquals(PerformanceMode.Performance, after.lastNonSaverMode)
    }

    @Test
    fun setPerformanceMode_toPowerSaver_remembersPriorTier() {
        val after = UserPrefs(
            performanceMode = PerformanceMode.Performance,
            lastNonSaverMode = PerformanceMode.Balanced,
        ).withPerformanceMode(PerformanceMode.PowerSaver)
        assertEquals(PerformanceMode.PowerSaver, after.performanceMode)
        assertEquals(PerformanceMode.Performance, after.lastNonSaverMode)
    }

    @Test
    fun powerSaverThenReduceMotionOff_stillRestoresLastNonSaver() {
        val afterPowerSaver = UserPrefs(
            performanceMode = PerformanceMode.Balanced,
            lastNonSaverMode = PerformanceMode.Balanced,
        ).withPowerSaverEnabled(true)
        val afterReduceOn = afterPowerSaver.withReduceMotion(true)
        val afterReduceOff = afterReduceOn.withReduceMotion(false)
        assertEquals(PerformanceMode.Balanced, afterReduceOff.performanceMode)
        assertFalse(afterReduceOff.reduceMotion)
    }
}
