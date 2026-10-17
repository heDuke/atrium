package com.alliehe.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.alliehe.core.data.AppIconLoader
import com.alliehe.core.data.IconStyle
import com.alliehe.core.model.AppEntry

/** Provided by the app root; null in previews (placeholder only). */
val LocalAppIconLoader = staticCompositionLocalOf<AppIconLoader?> { null }

/** Themed (monochrome-tinted) or full-colour icons; provided by the app root from the theme. */
val LocalIconStyle = staticCompositionLocalOf { IconStyle.Default }

/**
 * Loads [entry]'s icon lazily at the canonical size when this item is composed (i.e. visible in a
 * lazy list). Cache hits render immediately; otherwise a letter placeholder shows meanwhile.
 */
@Composable
fun rememberAppIcon(entry: AppEntry): ImageBitmap? {
    val loader = LocalAppIconLoader.current
    val style = LocalIconStyle.current
    val bitmap by produceState(loader?.peek(entry, style)?.asImageBitmap(), loader, entry, style) {
        if (value == null && loader != null) value = loader.load(entry, style)?.asImageBitmap()
    }
    return bitmap
}

@Composable
fun AppIcon(entry: AppEntry, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val image = rememberAppIcon(entry)
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(entry.label.take(1), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
