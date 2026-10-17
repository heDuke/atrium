package com.alliehe.core.color

/** Which palette actually got applied. */
enum class PaletteOrigin { Dynamic, UserSeed, WatchFaceSeed, Brand }

/** [seedArgb] is the seed fed to the generator; null only when [origin] is [PaletteOrigin.Dynamic]. */
data class ThemeResolution(
    val origin: PaletteOrigin,
    val seedArgb: Int?,
    /** True when the user asked for [ThemeSource.System] but system dynamic color is unavailable. */
    val systemUnavailable: Boolean = false,
)

/**
 * Locked chain: dynamic → seed (user seed, then own-watch-face seed) → brand.
 * - System: dynamic when available, otherwise falls through the seed chain.
 * - Seed: user seed → watch-face seed → brand (never dynamic).
 * - Brand: brand seed.
 */
object ThemeResolver {
    fun resolve(
        source: ThemeSource,
        dynamicAvailable: Boolean,
        userSeedArgb: Int?,
        watchFaceSeedArgb: Int?,
        brandSeedArgb: Int = BrandColors.BRAND_SEED,
    ): ThemeResolution = when (source) {
        ThemeSource.System ->
            if (dynamicAvailable) ThemeResolution(PaletteOrigin.Dynamic, null)
            else seedChain(userSeedArgb, watchFaceSeedArgb, brandSeedArgb).copy(systemUnavailable = true)
        ThemeSource.Seed -> seedChain(userSeedArgb, watchFaceSeedArgb, brandSeedArgb)
        ThemeSource.Brand -> ThemeResolution(PaletteOrigin.Brand, brandSeedArgb)
    }

    private fun seedChain(user: Int?, face: Int?, brand: Int): ThemeResolution = when {
        user != null -> ThemeResolution(PaletteOrigin.UserSeed, user)
        face != null -> ThemeResolution(PaletteOrigin.WatchFaceSeed, face)
        else -> ThemeResolution(PaletteOrigin.Brand, brand)
    }
}
