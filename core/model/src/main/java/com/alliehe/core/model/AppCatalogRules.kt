package com.alliehe.core.model

import java.text.Collator
import java.util.Locale

val AppEntry.componentKey: String
    get() = "$packageName/$activityName"

/**
 * Locale-aware alphabetical order: CJK labels sort by their pinyin ([AppEntry.romanized]) among Latin
 * labels, using a `Collator(Locale.CHINA)`; ties fall back to the label, then the component key.
 */
fun appAlphabeticalComparator(): Comparator<AppEntry> {
    val collator = Collator.getInstance(Locale.CHINA).apply { strength = Collator.SECONDARY }
    return Comparator<AppEntry> { a, b -> collator.compare(a.sortKey, b.sortKey) }
        .thenComparator { a, b -> collator.compare(a.label, b.label) }
        .thenBy { it.componentKey }
}

private val AppEntry.sortKey: String get() = romanized.ifEmpty { label.lowercase(Locale.ROOT) }

/**
 * Search: case-insensitive substring of label or package, pinyin without spaces ("weixin", "weix"),
 * or a prefix of the initials ("wx").
 */
fun AppEntry.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    if (label.contains(needle, true) || packageName.contains(needle, true)) return true
    val ascii = needle.lowercase(Locale.ROOT).replace(" ", "")
    if (ascii.isEmpty() || ascii.any { it !in 'a'..'z' && it !in '0'..'9' }) return false
    return romanized.replace(" ", "").contains(ascii) || initials.startsWith(ascii)
}

/** Shared drawer/search rules; preference identity intentionally remains package-based. */
fun List<AppEntry>.visibleApps(
    prefs: UserPrefs,
    query: String = "",
    prioritize: Boolean = true,
): List<AppEntry> {
    val visible = filterNot { it.packageName in prefs.hiddenPackages }.filter { it.matches(query) }
    val alphabetical = appAlphabeticalComparator()
    return visible.sortedWith(
        if (prioritize) compareByDescending<AppEntry> { it.packageName in prefs.pinnedPackages }
            .thenByDescending { it.packageName in prefs.favoritePackages }
            .then(alphabetical)
        else alphabetical,
    )
}

/** Drawer sections in display order: recents → pinned → favorites → all. Recents may repeat an app below. */
data class AppSections(
    val recents: List<AppEntry> = emptyList(),
    val pinned: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val all: List<AppEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && favorites.isEmpty() && all.isEmpty()
    /** Each visible app exactly once (recents excluded). */
    val flattened: List<AppEntry> get() = pinned + favorites + all
}

fun List<AppEntry>.sectioned(prefs: UserPrefs): AppSections {
    val sorted = visibleApps(prefs, prioritize = false)
    val pinned = sorted.filter { it.packageName in prefs.pinnedPackages }
    val favorites = sorted.filter { it.packageName !in prefs.pinnedPackages && it.packageName in prefs.favoritePackages }
    val rest = sorted.filter { it.packageName !in prefs.pinnedPackages && it.packageName !in prefs.favoritePackages }
    return AppSections(recentApps(prefs, sorted), pinned, favorites, rest)
}

/** Recently launched, most recent first; hidden or uninstalled apps are skipped. */
fun List<AppEntry>.recentApps(prefs: UserPrefs, visible: List<AppEntry> = visibleApps(prefs, prioritize = false)): List<AppEntry> {
    val byKey = visible.associateBy { it.componentKey }
    return prefs.recentComponents.mapNotNull { byKey[it] }.take(RECENTS_MAX)
}

/**
 * Applies a user-arranged order: apps listed in [order] come first in that order, the rest keep
 * their incoming order. Unknown keys (uninstalled apps) are ignored.
 */
fun <T> List<T>.withCustomOrder(order: List<String>, key: (T) -> String): List<T> {
    if (order.isEmpty()) return this
    val rank = HashMap<String, Int>(order.size).apply { order.forEachIndexed { i, k -> putIfAbsent(k, i) } }
    val (ranked, rest) = partition { key(it) in rank }
    return ranked.sortedBy { rank.getValue(key(it)) } + rest
}

/** Moves the item at [from] to [to] (clamped); out-of-range [from] returns the list unchanged. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (isEmpty() || from !in indices) return this
    val target = to.coerceIn(0, lastIndex)
    if (from == target) return this
    return toMutableList().apply { add(target, removeAt(from)) }
}
