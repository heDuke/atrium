package com.alliehe.core.model

import com.ibm.icu.text.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinAndOrderTest {
    private val icu = Transliterator.getInstance(PinyinKeys.TRANSLITERATOR_ID)
    private fun app(pkg: String, label: String) = AppEntry(pkg, "Main", label).withPinyin { icu.transliterate(it) }

    private val wechat = app("wx", "微信")
    private val alipay = app("ali", "支付宝")
    private val maps = app("maps", "Google Maps")
    private val clock = app("clock", "时钟")
    private val beta = app("b", "Beta")

    @Test fun romanizesHanAndKeepsLatin() {
        assertEquals("wei xin", wechat.romanized)
        assertEquals("wx", wechat.initials)
        assertEquals("google maps", maps.romanized)
        assertEquals("gm", maps.initials)
    }

    @Test fun searchByPinyinInitialsAndLabel() {
        assertTrue(wechat.matches("weixin"))
        assertTrue(wechat.matches("wei xin"))
        assertTrue(wechat.matches("WX"))
        assertTrue(wechat.matches("微"))
        assertTrue(alipay.matches("zfb"))
        assertTrue(alipay.matches("zhifu"))
        assertTrue(maps.matches("gm"))
        assertFalse(wechat.matches("zfb"))
        assertFalse(wechat.matches("x w"))
    }

    @Test fun searchGoesThroughVisibleApps() {
        val apps = listOf(wechat, alipay, maps)
        assertEquals(listOf(alipay), apps.visibleApps(UserPrefs(), "zfb"))
        assertEquals(emptyList<AppEntry>(), apps.visibleApps(UserPrefs(hiddenPackages = setOf("ali")), "zfb"))
    }

    @Test fun mixedScriptsSortByPinyin() {
        // beta < google < shi zhong < wei xin < zhi fu bao
        val sorted = listOf(alipay, wechat, clock, maps, beta).visibleApps(UserPrefs(), prioritize = false)
        assertEquals(listOf(beta, maps, clock, wechat, alipay), sorted)
    }

    @Test fun recentsAreMostRecentFirstDedupedAndCapped() {
        var r = emptyList<String>()
        listOf("a", "b", "c", "a", "d", "e", "f").forEach { r = recordRecent(r, it) }
        assertEquals(listOf("f", "e", "d", "a", "c"), r)
        assertEquals(RECENTS_MAX, r.size)
    }

    @Test fun recentsSectionSkipsHiddenAndMissing() {
        val apps = listOf(wechat, alipay, maps)
        val prefs = UserPrefs(
            recentComponents = listOf(maps.componentKey, "gone/Main", alipay.componentKey, wechat.componentKey),
            hiddenPackages = setOf("ali"),
        )
        assertEquals(listOf(maps, wechat), apps.sectioned(prefs).recents)
        // Recents do not remove the app from its regular section.
        assertTrue(maps in apps.sectioned(prefs).flattened)
    }

    @Test fun customOrderPutsArrangedFirstAndIgnoresUnknown() {
        val items = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), items.withCustomOrder(listOf("c", "zz", "a")) { it })
        assertEquals(items, items.withCustomOrder(emptyList()) { it })
    }

    @Test fun movedReordersAndClamps() {
        val keys = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), keys.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), keys.moved(3, -5))
        assertEquals(keys, keys.moved(9, 0))
        // Persisted order round-trips through withCustomOrder.
        val order = keys.moved(3, 0)
        assertEquals(order, keys.withCustomOrder(order) { it })
    }
}
