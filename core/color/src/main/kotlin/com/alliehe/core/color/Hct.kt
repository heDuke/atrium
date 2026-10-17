package com.alliehe.core.color

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HCT = CAM16 hue + CAM16 chroma + CIE L* tone, written from the published formulas:
 * - sRGB (IEC 61966-2-1) transfer function and D65 RGB→XYZ matrix,
 * - CIE 1976 L* ↔ Y,
 * - CAM16 forward model and inverse (Li, Li, Wang, Zu, Luo, Cui, Melgosa, Brill, Pointer;
 *   "Comprehensive color solutions: CAM16, CAT16, and CAM16-UCS", Color Res. Appl. 2017).
 *
 * Viewing conditions: D65 white, background L* = 50, adapting luminance = 200/π · Y(L*50)/100,
 * average surround (c = 0.69, Nc = F = 1), no discounting.
 *
 * The C++ port in `core/color-native/src/main/cpp/color_core.cpp` mirrors this file statement by
 * statement (same operation order) so both produce identical ARGB within ±1 per channel.
 */
object Hct {
    // sRGB (D65) linear RGB → XYZ, rows X, Y, Z.
    private val RGB_TO_XYZ = doubleArrayOf(
        0.4124, 0.3576, 0.1805,
        0.2126, 0.7152, 0.0722,
        0.0193, 0.1192, 0.9505,
    )
    private val XYZ_TO_RGB = invert3(RGB_TO_XYZ)

    // CAT16 (Li et al. 2017).
    private val M16 = doubleArrayOf(
        0.401288, 0.650173, -0.051461,
        -0.250268, 1.204414, 0.045854,
        -0.002079, 0.048952, 0.953127,
    )
    private val M16_INV = invert3(M16)

    // D65 reference white (Y = 100). Note: with incomplete adaptation (D < 1) sRGB grays keep a small CAM16
    // chroma (white ≈ 2.9 at hue ≈ 209.5); callers treat chroma < 4 as achromatic.
    private val WHITE = doubleArrayOf(95.047, 100.0, 108.883)

    // ---- viewing conditions (computed once) ----
    private const val C_SURROUND = 0.69
    private const val NC = 1.0
    private val YB = yFromLstar(50.0)
    private val LA = 200.0 / PI * YB / 100.0
    private val D: Double
    private val DRGB = DoubleArray(3)
    private val FL: Double
    private val N: Double = YB / WHITE[1]
    private val Z: Double = 1.48 + sqrt(N)
    private val NBB: Double = 0.725 / N.pow(0.2)
    private val AW: Double
    private val CHROMA_FACTOR: Double = (1.64 - 0.29.pow(N)).pow(0.73)

    init {
        D = (1.0 * (1.0 - (1.0 / 3.6) * exp((-LA - 42.0) / 92.0))).coerceIn(0.0, 1.0)
        val rgbW = mul3(M16, WHITE)
        for (i in 0..2) DRGB[i] = D * (100.0 / rgbW[i]) + 1.0 - D
        val k = 1.0 / (5.0 * LA + 1.0)
        val k4 = k * k * k * k
        FL = k4 * LA + 0.1 * (1.0 - k4) * (1.0 - k4) * cbrt(5.0 * LA)
        val ra = adapt(DRGB[0] * rgbW[0])
        val ga = adapt(DRGB[1] * rgbW[1])
        val ba = adapt(DRGB[2] * rgbW[2])
        AW = (2.0 * ra + ga + ba / 20.0 - 0.305) * NBB
    }

    // ---- public API ----

    /** [hue 0..360, chroma ≥ 0, tone 0..100] of [argb]. */
    fun fromArgb(argb: Int): DoubleArray {
        val xyz = argbToXyz(argb)
        val tone = lstarFromY(xyz[1])
        val rgb = mul3(M16, xyz)
        val ra = adapt(DRGB[0] * rgb[0])
        val ga = adapt(DRGB[1] * rgb[1])
        val ba = adapt(DRGB[2] * rgb[2])
        val a = ra - 12.0 * ga / 11.0 + ba / 11.0
        val b = (ra + ga - 2.0 * ba) / 9.0
        var hue = atan2(b, a) * 180.0 / PI
        if (hue < 0.0) hue += 360.0
        val bigA = (2.0 * ra + ga + ba / 20.0 - 0.305) * NBB
        if (bigA <= 0.0) return doubleArrayOf(hue, 0.0, tone)
        val j = 100.0 * (bigA / AW).pow(C_SURROUND * Z)
        val et = 0.25 * (cos(hue * PI / 180.0 + 2.0) + 3.8)
        val t = (50000.0 / 13.0 * NC * NBB * et * hypot(a, b)) / (ra + ga + 21.0 / 20.0 * ba)
        val chroma = t.pow(0.9) * sqrt(j / 100.0) * CHROMA_FACTOR
        return doubleArrayOf(hue, chroma, tone)
    }

    /**
     * ARGB with CAM16 [hue], the largest in-gamut chroma ≤ [chroma], and L* == [tone].
     * Tone is solved by bisection on CAM16 J; out-of-gamut chroma is reduced by bisection.
     */
    fun toArgb(hue: Double, chroma: Double, tone: Double): Int {
        if (tone <= 0.0001) return 0xFF000000.toInt()
        if (tone >= 99.9999) return 0xFFFFFFFF.toInt()
        val targetY = yFromLstar(tone)
        val h = sanitizeHue(hue)
        if (chroma < 0.5) return grayFromY(targetY)
        solveForY(h, chroma, targetY)?.let { return xyzToArgb(it) }
        var lo = 0.0
        var hi = chroma
        var best: DoubleArray? = null
        repeat(CHROMA_ITERATIONS) {
            val mid = (lo + hi) / 2.0
            val xyz = solveForY(h, mid, targetY)
            if (xyz != null) { best = xyz; lo = mid } else hi = mid
        }
        return best?.let { xyzToArgb(it) } ?: grayFromY(targetY)
    }

    fun toneOf(argb: Int): Double = lstarFromY(argbToXyz(argb)[1])

    // ---- CIE L* ----

    fun yFromLstar(lstar: Double): Double {
        val ft = (lstar + 16.0) / 116.0
        val ft3 = ft * ft * ft
        val y = if (ft3 > EPSILON) ft3 else (116.0 * ft - 16.0) / KAPPA
        return y * 100.0
    }

    fun lstarFromY(y: Double): Double {
        val yr = y / 100.0
        val f = if (yr > EPSILON) cbrt(yr) else (KAPPA * yr + 16.0) / 116.0
        return 116.0 * f - 16.0
    }

    /** WCAG 2.x contrast ratio of two ARGB colors (relative luminance uses Y/100). */
    fun contrastRatio(a: Int, b: Int): Double {
        val ya = argbToXyz(a)[1]
        val yb = argbToXyz(b)[1]
        val hi = maxOf(ya, yb)
        val lo = minOf(ya, yb)
        return (hi + 5.0) / (lo + 5.0)
    }

    // ---- internals ----

    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0
    private const val J_ITERATIONS = 26
    private const val CHROMA_ITERATIONS = 14
    private const val GAMUT_EPS = 1e-4

    private fun sanitizeHue(h: Double): Double {
        val r = h - 360.0 * floor(h / 360.0)
        return if (r >= 360.0) 0.0 else r
    }

    /** XYZ (Y 0..100) for (h, chroma) whose Y == [targetY], or null if out of sRGB gamut. */
    private fun solveForY(h: Double, chroma: Double, targetY: Double): DoubleArray? {
        var lo = 0.0
        var hi = 100.0
        repeat(J_ITERATIONS) {
            val j = (lo + hi) / 2.0
            val y = camToXyz(j, chroma, h)?.get(1) ?: Double.NaN
            if (y.isNaN() || y > targetY) hi = j else lo = j
        }
        val result = camToXyz((lo + hi) / 2.0, chroma, h) ?: return null
        if (abs(result[1] - targetY) > 0.01 * maxOf(1.0, targetY)) return null
        val lin = mul3(XYZ_TO_RGB, result)
        for (c in lin) if (c < -GAMUT_EPS * 100.0 || c > 100.0 + GAMUT_EPS * 100.0) return null
        return result
    }

    /** CAM16 inverse (J, C, h) → XYZ, or null when the nonlinearity cannot be inverted. */
    private fun camToXyz(j: Double, chroma: Double, h: Double): DoubleArray? {
        if (j <= 0.0) return doubleArrayOf(0.0, 0.0, 0.0)
        val t = if (chroma == 0.0) 0.0 else (chroma / (sqrt(j / 100.0) * CHROMA_FACTOR)).pow(1.0 / 0.9)
        val bigA = AW * (j / 100.0).pow(1.0 / (C_SURROUND * Z))
        val p2 = bigA / NBB + 0.305
        val p3 = 21.0 / 20.0
        val hr = h * PI / 180.0
        val s = sin(hr)
        val co = cos(hr)
        val et = 0.25 * (cos(hr + 2.0) + 3.8)
        var a = 0.0
        var b = 0.0
        if (t != 0.0) {
            val p1 = (50000.0 / 13.0) * NC * NBB * et / t
            if (abs(s) >= abs(co)) {
                val p4 = p1 / s
                b = p2 * (2.0 + p3) * (460.0 / 1403.0) /
                    (p4 + (2.0 + p3) * (220.0 / 1403.0) * (co / s) - 27.0 / 1403.0 + p3 * (6300.0 / 1403.0))
                a = b * co / s
            } else {
                val p5 = p1 / co
                a = p2 * (2.0 + p3) * (460.0 / 1403.0) /
                    (p5 + (2.0 + p3) * (220.0 / 1403.0) - (27.0 / 1403.0 - p3 * (6300.0 / 1403.0)) * (s / co))
                b = a * s / co
            }
        }
        val ra = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val ga = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val ba = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        val rc = unadapt(ra)
        val gc = unadapt(ga)
        val bc = unadapt(ba)
        if (rc.isNaN() || gc.isNaN() || bc.isNaN()) return null
        return mul3(M16_INV, doubleArrayOf(rc / DRGB[0], gc / DRGB[1], bc / DRGB[2]))
    }

    private fun adapt(x: Double): Double {
        val p = (FL * abs(x) / 100.0).pow(0.42)
        return sign(x) * 400.0 * p / (p + 27.13) + 0.1
    }

    private fun unadapt(x: Double): Double {
        val y = x - 0.1
        val ay = abs(y)
        if (ay >= 400.0) return Double.NaN
        return sign(y) * (100.0 / FL) * ((27.13 * ay) / (400.0 - ay)).pow(1.0 / 0.42)
    }

    private fun grayFromY(y: Double): Int {
        val c = delinearize(y)
        return argb(c, c, c)
    }

    private fun argbToXyz(argb: Int): DoubleArray {
        val r = linearize((argb shr 16) and 0xFF)
        val g = linearize((argb shr 8) and 0xFF)
        val b = linearize(argb and 0xFF)
        return mul3(RGB_TO_XYZ, doubleArrayOf(r, g, b))
    }

    private fun xyzToArgb(xyz: DoubleArray): Int {
        val lin = mul3(XYZ_TO_RGB, xyz)
        return argb(delinearize(lin[0]), delinearize(lin[1]), delinearize(lin[2]))
    }

    /** 0..255 channel → linear 0..100. */
    private fun linearize(c: Int): Double {
        val v = c / 255.0
        val l = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return l * 100.0
    }

    /** linear 0..100 → 0..255 channel (rounded, clamped). */
    private fun delinearize(l: Double): Int {
        val v = l / 100.0
        val e = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1.0 / 2.4) - 0.055
        return round(e * 255.0).toInt().coerceIn(0, 255)
    }

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun mul3(m: DoubleArray, v: DoubleArray) = doubleArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
    )

    private fun invert3(m: DoubleArray): DoubleArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        return doubleArrayOf(
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det,
        )
    }
}
