package com.alliehe.feature.tile

import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * F4: provides Tile content only. Add/remove/reorder is System UI (see G2).
 * ProtoLayout Material3 tree — must not share App Compose UI tree.
 */
class AtriumTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val title = getString(R.string.tile_placeholder_title)
        val body = getString(R.string.tile_placeholder_body)

        val layout = materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = {
                    text(LayoutString(title))
                },
                mainSlot = {
                    text(LayoutString(body))
                },
            )
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(expand())
                        .setHeight(expand())
                        .addContent(layout)
                        .build(),
                ),
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

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
