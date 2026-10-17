package com.alliehe.core.model

import java.util.Locale

/**
 * Search/sort keys for a label. [transliterate] is ICU "Han-Latin; Latin-ASCII" (android.icu on device,
 * icu4j in JVM tests); it turns "微信" into "wei xin" and leaves Latin text as is.
 */
object PinyinKeys {
    const val TRANSLITERATOR_ID = "Han-Latin; Latin-ASCII"

    fun of(label: String, transliterate: (String) -> String): Pair<String, String> {
        val hasHan = label.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val latin = if (hasHan) runCatching { transliterate(label) }.getOrDefault(label) else label
        val romanized = latin.lowercase(Locale.ROOT)
            .map { if (it in 'a'..'z' || it in '0'..'9') it else ' ' }
            .joinToString("")
            .split(' ').filter { it.isNotEmpty() }
            .joinToString(" ")
        val initials = romanized.split(' ').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
        return romanized to initials
    }
}

fun AppEntry.withPinyin(transliterate: (String) -> String): AppEntry {
    val (romanized, initials) = PinyinKeys.of(label, transliterate)
    return copy(romanized = romanized, initials = initials)
}
