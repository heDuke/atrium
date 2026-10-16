package com.alliehe.core.model

import java.util.Locale

val AppEntry.componentKey: String
    get() = "$packageName/$activityName"

/** Shared drawer/search rules; preference identity intentionally remains package-based. */
fun List<AppEntry>.visibleApps(
    prefs: UserPrefs,
    query: String = "",
    prioritize: Boolean = true,
): List<AppEntry> {
    val needle = query.trim()
    val visible = filterNot { it.packageName in prefs.hiddenPackages }
        .filter { needle.isEmpty() || it.label.contains(needle, true) || it.packageName.contains(needle, true) }
    val alphabetical = compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }
        .thenBy { it.componentKey }
    return visible.sortedWith(
        if (prioritize) compareByDescending<AppEntry> { it.packageName in prefs.pinnedPackages }
            .thenByDescending { it.packageName in prefs.favoritePackages }
            .then(alphabetical)
        else alphabetical,
    )
}

/** Drawer sections in display order: pinned → favorites → all (each app appears once). */
data class AppSections(
    val pinned: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val all: List<AppEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && favorites.isEmpty() && all.isEmpty()
    val flattened: List<AppEntry> get() = pinned + favorites + all
}

fun List<AppEntry>.sectioned(prefs: UserPrefs): AppSections {
    val sorted = visibleApps(prefs, prioritize = false)
    val pinned = sorted.filter { it.packageName in prefs.pinnedPackages }
    val favorites = sorted.filter { it.packageName !in prefs.pinnedPackages && it.packageName in prefs.favoritePackages }
    val rest = sorted.filter { it.packageName !in prefs.pinnedPackages && it.packageName !in prefs.favoritePackages }
    return AppSections(pinned, favorites, rest)
}
