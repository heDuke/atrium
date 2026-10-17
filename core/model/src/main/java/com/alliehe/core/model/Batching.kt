package com.alliehe.core.model

/**
 * Index ranges for incremental catalog emission: a small first batch (first screen),
 * then fixed-size batches for the rest.
 */
fun batchRanges(total: Int, first: Int, rest: Int): List<IntRange> {
    require(first > 0 && rest > 0)
    if (total <= 0) return emptyList()
    val out = mutableListOf(0 until minOf(first, total))
    var start = first
    while (start < total) {
        out += start until minOf(start + rest, total)
        start += rest
    }
    return out
}
