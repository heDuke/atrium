package com.alliehe.core.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.wear.compose.material3.SurfaceTransformation

/** For text and groups without their own morphing surface; transform the group exactly once. */
@Composable
fun TransformingContent(
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .graphicsLayer { transformation.run { applyContainerTransformation() } }
            .then(modifier)
            .graphicsLayer { transformation.run { applyContentTransformation() } },
        content = content,
    )
}
