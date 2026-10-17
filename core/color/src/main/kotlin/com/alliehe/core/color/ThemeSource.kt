package com.alliehe.core.color

/** Where the app palette comes from (user setting). Default [System]. */
enum class ThemeSource {
    /** System dynamic color (on Wear OS this follows the watch face colors) when available. */
    System,

    /** A user-picked seed color (8 presets or custom hue) run through the HCT tonal pipeline. */
    Seed,

    /** Atrium's brand seed run through the same pipeline. */
    Brand,
}
