package com.alliehe.feature.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.alliehe.core.data.UserPrefsReader
import com.alliehe.core.model.PerformanceMode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * F4: Tile content only. Add/remove/reorder is System UI (see G2).
 * ProtoLayout Material3 tree — must not share App Compose UI tree.
 *
 * Refresh cadence follows G5 via [UserPrefsReader] + integer resources
 * (Performance frequent / Balanced default / PowerSaver longer).
 */
class AtriumTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val title = getString(R.string.tile_placeholder_title)
        val body = getString(R.string.tile_placeholder_body)
        val openLabel = getString(R.string.tile_open_drawer)
        val openDescription = getString(R.string.tile_open_drawer_cd)

        val openClickable = clickable(
            id = CLICK_OPEN_MAIN,
            action = launchMainActivityAction(),
        )

        val layout = materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = {
                    text(LayoutString(title))
                },
                mainSlot = {
                    text(LayoutString(body))
                },
                bottomSlot = {
                    textEdgeButton(
                        onClick = openClickable,
                        modifier = LayoutModifier.contentDescription(openDescription),
                    ) {
                        text(LayoutString(openLabel))
                    }
                },
                onClick = openClickable,
            )
        }

        val prefs = UserPrefsReader.currentOrDefault(this)
        val freshnessMs = freshnessIntervalMs(prefs.effectivePerformanceMode)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(freshnessMs)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(layout),
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )
    }

    private fun launchMainActivityAction(): ActionBuilders.LaunchAction =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MAIN_ACTIVITY_CLASS)
                    .build(),
            )
            .build()

    private fun freshnessIntervalMs(mode: PerformanceMode): Long {
        val res = when (mode) {
            PerformanceMode.Performance -> R.integer.tile_freshness_performance_ms
            PerformanceMode.Balanced -> R.integer.tile_freshness_balanced_ms
            PerformanceMode.PowerSaver -> R.integer.tile_freshness_power_saver_ms
        }
        return resources.getInteger(res).toLong()
    }

    private companion object {
        /** Bump when tile image / string resources change. */
        const val RESOURCES_VERSION = "4"

        const val CLICK_OPEN_MAIN = "open_main"

        /** App MAIN+LAUNCHER; tile module must not depend on :app. */
        const val MAIN_ACTIVITY_CLASS = "com.alliehe.atrium.MainActivity"
    }
}
