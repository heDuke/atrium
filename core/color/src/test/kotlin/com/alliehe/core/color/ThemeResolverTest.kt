package com.alliehe.core.color

import org.junit.Assert.assertEquals
import org.junit.Test

/** Truth table: source × dynamic availability × user seed × watch-face seed. */
class ThemeResolverTest {
    private val user = 0xFF4285F4.toInt()
    private val face = 0xFFE53935.toInt()
    private val brand = BrandColors.BRAND_SEED

    private data class Row(
        val source: ThemeSource, val dynamic: Boolean, val user: Int?, val face: Int?,
        val origin: PaletteOrigin, val seed: Int?, val unavailable: Boolean,
    )

    private val table = listOf(
        Row(ThemeSource.System, true, null, null, PaletteOrigin.Dynamic, null, false),
        Row(ThemeSource.System, true, user, face, PaletteOrigin.Dynamic, null, false),
        Row(ThemeSource.System, false, null, null, PaletteOrigin.Brand, brand, true),
        Row(ThemeSource.System, false, user, null, PaletteOrigin.UserSeed, user, true),
        Row(ThemeSource.System, false, null, face, PaletteOrigin.WatchFaceSeed, face, true),
        Row(ThemeSource.System, false, user, face, PaletteOrigin.UserSeed, user, true),
        Row(ThemeSource.Seed, true, user, face, PaletteOrigin.UserSeed, user, false),
        Row(ThemeSource.Seed, false, user, null, PaletteOrigin.UserSeed, user, false),
        Row(ThemeSource.Seed, true, null, face, PaletteOrigin.WatchFaceSeed, face, false),
        Row(ThemeSource.Seed, true, null, null, PaletteOrigin.Brand, brand, false),
        Row(ThemeSource.Seed, false, null, null, PaletteOrigin.Brand, brand, false),
        Row(ThemeSource.Brand, true, user, face, PaletteOrigin.Brand, brand, false),
        Row(ThemeSource.Brand, false, user, face, PaletteOrigin.Brand, brand, false),
        Row(ThemeSource.Brand, false, null, null, PaletteOrigin.Brand, brand, false),
    )

    @Test
    fun truthTable() {
        for (r in table) {
            val res = ThemeResolver.resolve(r.source, r.dynamic, r.user, r.face)
            assertEquals(r.toString(), ThemeResolution(r.origin, r.seed, r.unavailable), res)
        }
    }

    @Test
    fun everyCombinationYieldsAPalette() {
        for (s in ThemeSource.entries) for (d in listOf(true, false)) for (u in listOf(null, user)) for (f in listOf(null, face)) {
            val res = ThemeResolver.resolve(s, d, u, f)
            assertEquals(res.origin == PaletteOrigin.Dynamic, res.seedArgb == null)
        }
    }
}
