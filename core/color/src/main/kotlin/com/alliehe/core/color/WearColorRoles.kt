package com.alliehe.core.color

/**
 * All Wear Material 3 `ColorScheme` roles as ARGB ints. Field order == [toIntArray] order == the
 * order the native generator writes (see `core/color-native/src/main/cpp/color_core.h`).
 */
data class WearColorRoles(
    val primary: Int,
    val primaryDim: Int,
    val primaryContainer: Int,
    val onPrimary: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val secondaryDim: Int,
    val secondaryContainer: Int,
    val onSecondary: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryDim: Int,
    val tertiaryContainer: Int,
    val onTertiary: Int,
    val onTertiaryContainer: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val background: Int,
    val onBackground: Int,
    val error: Int,
    val errorDim: Int,
    val errorContainer: Int,
    val onError: Int,
    val onErrorContainer: Int,
) {
    fun toIntArray(): IntArray = intArrayOf(
        primary, primaryDim, primaryContainer, onPrimary, onPrimaryContainer,
        secondary, secondaryDim, secondaryContainer, onSecondary, onSecondaryContainer,
        tertiary, tertiaryDim, tertiaryContainer, onTertiary, onTertiaryContainer,
        surfaceContainerLow, surfaceContainer, surfaceContainerHigh, onSurface, onSurfaceVariant,
        outline, outlineVariant, background, onBackground,
        error, errorDim, errorContainer, onError, onErrorContainer,
    )

    companion object {
        const val ROLE_COUNT = 29

        val ROLE_NAMES: List<String> = listOf(
            "primary", "primaryDim", "primaryContainer", "onPrimary", "onPrimaryContainer",
            "secondary", "secondaryDim", "secondaryContainer", "onSecondary", "onSecondaryContainer",
            "tertiary", "tertiaryDim", "tertiaryContainer", "onTertiary", "onTertiaryContainer",
            "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "onSurface", "onSurfaceVariant",
            "outline", "outlineVariant", "background", "onBackground",
            "error", "errorDim", "errorContainer", "onError", "onErrorContainer",
        )

        fun fromIntArray(a: IntArray): WearColorRoles {
            require(a.size == ROLE_COUNT) { "expected $ROLE_COUNT roles, got ${a.size}" }
            return WearColorRoles(
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9],
                a[10], a[11], a[12], a[13], a[14], a[15], a[16], a[17], a[18], a[19],
                a[20], a[21], a[22], a[23], a[24], a[25], a[26], a[27], a[28],
            )
        }
    }
}
