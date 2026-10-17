package com.alliehe.feature.tile

import com.alliehe.core.color.BrandColors
import com.alliehe.core.color.KotlinSchemeGenerator
import com.alliehe.core.color.ThemeSource
import com.alliehe.core.model.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class TileThemeTest {
    private val gen = KotlinSchemeGenerator()

    @Test fun brandSourceIgnoresUserSeed() {
        val roles = tileRoles(UserPrefs(themeSource = ThemeSource.Brand, seedColorArgb = 0xFF4285F4), gen)
        assertEquals(gen.generate(BrandColors.BRAND_SEED), roles)
    }

    @Test fun seedAndSystemFallbackUseUserSeed() {
        val blue = gen.generate(0xFF4285F4.toInt())
        assertEquals(blue, tileRoles(UserPrefs(themeSource = ThemeSource.Seed, seedColorArgb = 0xFF4285F4), gen))
        assertEquals(blue, tileRoles(UserPrefs(themeSource = ThemeSource.System, seedColorArgb = 0xFF4285F4), gen))
    }

    @Test fun backgroundIsBlack() {
        assertEquals(0xFF000000.toInt(), tileRoles(UserPrefs(), gen).background)
    }
}
