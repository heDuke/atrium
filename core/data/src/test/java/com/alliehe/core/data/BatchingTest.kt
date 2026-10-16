package com.alliehe.core.data

import com.alliehe.core.model.batchRanges
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchingTest {
    @Test fun firstBatchIsSmallThenFixedBatchesCoverEverythingOnce() {
        val r = batchRanges(70, 12, 24)
        assertEquals(listOf(0 until 12, 12 until 36, 36 until 60, 60 until 70), r)
        assertEquals((0 until 70).toList(), r.flatMap { it.toList() })
    }
    @Test fun smallCatalogIsOneBatch() = assertEquals(listOf(0 until 5), batchRanges(5, 12, 24))
    @Test fun emptyCatalogHasNoBatches() = assertEquals(emptyList<IntRange>(), batchRanges(0, 12, 24))
}
