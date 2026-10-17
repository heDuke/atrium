package com.alliehe.core.color

/**
 * Dominant-color extraction shared (algorithmically) with `color_core.cpp` (`extract_seed`):
 * 1. Sample a ≤ 64×64 grid (step = ceil(max(w, h) / 64)); skip alpha < 128.
 * 2. Quantize into a 15-bit RGB histogram (5 bits per channel).
 * 3. For each occupied bin, compute HCT of the bin centre; drop grays (chroma < 15), near-black
 *    (tone < 12) and near-white (tone > 92).
 * 4. Accumulate into 36 hue buckets (10°); score = smoothed proportion × mean chroma
 *    (neighbour buckets weighted 0.5; proportion over all opaque samples). Buckets < 1 % are ignored.
 * 5. Return the bin with the largest count × chroma inside the best bucket.
 */
object SeedExtractor {
    const val MAX_SIDE = 64
    private const val BUCKETS = 36

    fun extract(pixels: IntArray, width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val step = maxOf(1, (maxOf(width, height) + MAX_SIDE - 1) / MAX_SIDE)
        val hist = IntArray(1 shl 15)
        var total = 0
        var y = 0
        while (y < height) {
            var x = 0
            val row = y * width
            while (x < width) {
                val p = pixels[row + x]
                if ((p ushr 24) >= 128) {
                    val bin = (((p shr 19) and 0x1F) shl 10) or (((p shr 11) and 0x1F) shl 5) or ((p shr 3) and 0x1F)
                    hist[bin]++
                    total++
                }
                x += step
            }
            y += step
        }
        return pick(hist, total)
    }

    /** Steps 3–5 on a filled histogram. Exposed so callers with their own sampler share the scoring. */
    fun pick(hist: IntArray, total: Int): Int? {
        if (total == 0) return null
        val count = DoubleArray(BUCKETS)
        val chromaSum = DoubleArray(BUCKETS)
        val bestScore = DoubleArray(BUCKETS)
        val bestColor = IntArray(BUCKETS)
        for (bin in hist.indices) {
            val n = hist[bin]
            if (n == 0) continue
            val r = (((bin shr 10) and 0x1F) shl 3) or 4
            val g = (((bin shr 5) and 0x1F) shl 3) or 4
            val b = ((bin and 0x1F) shl 3) or 4
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val hct = Hct.fromArgb(argb)
            if (hct[1] < 15.0 || hct[2] < 12.0 || hct[2] > 92.0) continue
            val bucket = (hct[0] / 10.0).toInt().coerceIn(0, BUCKETS - 1)
            count[bucket] += n.toDouble()
            chromaSum[bucket] += hct[1] * n
            val s = n * hct[1]
            if (s > bestScore[bucket]) { bestScore[bucket] = s; bestColor[bucket] = argb }
        }
        var winner = -1
        var winnerScore = 0.0
        for (i in 0 until BUCKETS) {
            if (count[i] == 0.0) continue
            val smoothed = (count[(i + BUCKETS - 1) % BUCKETS] * 0.5 + count[i] + count[(i + 1) % BUCKETS] * 0.5) / total
            if (count[i] / total < 0.01) continue
            val score = smoothed * (chromaSum[i] / count[i])
            if (score > winnerScore) { winnerScore = score; winner = i }
        }
        return if (winner < 0) null else bestColor[winner]
    }
}
