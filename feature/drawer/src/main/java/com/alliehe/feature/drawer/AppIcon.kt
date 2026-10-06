package com.alliehe.feature.drawer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text

@Composable
internal fun AppIcon(bitmap: Bitmap?, label: String) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(32.dp))
    } else {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Text(label.take(1))
        }
    }
}
