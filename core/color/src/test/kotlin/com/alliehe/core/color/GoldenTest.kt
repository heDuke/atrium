package com.alliehe.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Golden ARGB for seeds and extraction. Regenerate deliberately with
 * `./gradlew :core:color:test -Patrium.updateGolden=true` (then re-run the native host test).
 */
class GoldenTest {
    private val generator = KotlinSchemeGenerator()

    private fun render(): List<String> {
        val lines = ArrayList<String>()
        for ((seed, contrast) in GoldenFixtures.SEEDS) {
            val roles = generator.generate(seed, contrast).toIntArray()
            lines += "scheme ${GoldenFixtures.hex(seed)} $contrast " + roles.joinToString(" ") { GoldenFixtures.hex(it) }
        }
        for (name in GoldenFixtures.IMAGES) {
            val (px, w, h) = GoldenFixtures.image(name)
            lines += "extract $name ${GoldenFixtures.hex(generator.extractSeed(px, w, h) ?: 0)}"
        }
        return lines
    }

    @Test
    fun matchesGolden() {
        val actual = render()
        val file = GoldenFixtures.goldenFile
        if (System.getProperty("atrium.updateGolden") == "true") {
            file.parentFile.mkdirs()
            file.writeText(actual.joinToString("\n", postfix = "\n"))
            return
        }
        if (!file.exists()) fail("missing ${file.absolutePath}; run with -Patrium.updateGolden=true")
        val expected = file.readLines().filter { it.isNotBlank() }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertLineClose(e, a) }
    }

    /** Same tolerance as the C++ host test: ±1 per channel. */
    private fun assertLineClose(expected: String, actual: String) {
        val e = expected.split(" ")
        val a = actual.split(" ")
        assertEquals(expected, e.size, a.size)
        val firstColor = if (e[0] == "scheme") 3 else 2
        for (i in 0 until firstColor) assertEquals(expected, e[i], a[i])
        for (i in firstColor until e.size) {
            val x = e[i].toLong(16).toInt()
            val y = a[i].toLong(16).toInt()
            for (shift in intArrayOf(24, 16, 8, 0)) {
                val d = ((x shr shift) and 0xFF) - ((y shr shift) and 0xFF)
                if (d !in -1..1) fail("$expected\n!=\n$actual (field $i)")
            }
        }
    }
}
