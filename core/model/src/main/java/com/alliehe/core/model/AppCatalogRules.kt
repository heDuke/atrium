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
