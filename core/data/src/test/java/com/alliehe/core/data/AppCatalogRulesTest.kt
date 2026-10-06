package com.alliehe.core.data

import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.UserPrefs
import com.alliehe.core.model.componentKey
import com.alliehe.core.model.visibleApps
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogRulesTest {
    private val a = AppEntry("a", "Main", "Alpha")
    private val b = AppEntry("b", "Main", "Beta")
    private val c = AppEntry("c", "Main", "Gamma")

    @Test fun hiddenCannotLeakThroughSearchAndCanBeRestored() {
        val apps = listOf(a, b, c)
        val hidden = UserPrefs(hiddenPackages = setOf("b"))
        assertEquals(emptyList<AppEntry>(), apps.visibleApps(hidden, "Beta"))
        assertEquals(listOf(b), apps.visibleApps(hidden.copy(hiddenPackages = emptySet()), "Beta"))
    }
    @Test fun pinnedPrecedesFavoriteAndAlphabetical() {
        assertEquals(listOf(c, b, a), listOf(a, b, c).visibleApps(
            UserPrefs(pinnedPackages = setOf("c"), favoritePackages = setOf("b")),
        ))
    }
    @Test fun searchMatchesTrimmedCaseInsensitivePackageOrLabel() {
        assertEquals(listOf(a), listOf(a, b).visibleApps(UserPrefs(), " ALPHA "))
        assertEquals(listOf(b), listOf(a, b).visibleApps(UserPrefs(), "B"))
    }
    @Test fun componentIdentityDoesNotCollapseTwoActivitiesOfOnePackage() {
        val second = a.copy(activityName = "Other")
        assertEquals(2, listOf(a, second).map { it.componentKey }.distinct().size)
        assertEquals(emptyList<AppEntry>(), listOf(a, second).visibleApps(UserPrefs(hiddenPackages = setOf("a"))))
    }
}
