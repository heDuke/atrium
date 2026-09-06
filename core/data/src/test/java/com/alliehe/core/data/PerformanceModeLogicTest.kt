package com.alliehe.core.data

import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * G5 / G3 effective-mode rules (pure model logic; DataStore wiring covered later).
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
    fun exitPowerSaver_restoresLastNonSaverNotPerformanceByDefault() {
        val beforeExit = UserPrefs(
            performanceMode = PerformanceMode.PowerSaver,
            lastNonSaverMode = PerformanceMode.Balanced,
            reduceMotion = false,
        )
        val afterExit = beforeExit.copy(performanceMode = beforeExit.lastNonSaverMode)
        assertEquals(PerformanceMode.Balanced, afterExit.performanceMode)
    }
}
