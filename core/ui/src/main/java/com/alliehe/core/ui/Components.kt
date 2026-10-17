package com.alliehe.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.theme.TransformingContent

/** Section title inside a list (screen titles keep ListHeader). Marked as a heading for TalkBack. */
@Composable
fun TransformingLazyColumnItemScope.SectionHeader(text: String, spec: TransformationSpec) {
    ListSubHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).semantics { heading() },
        transformation = SurfaceTransformation(spec),
    ) { Text(text) }
}

/** Centered hint text, optionally followed by a single action (empty states). */
@Composable
fun TransformingLazyColumnItemScope.EmptyState(
    text: String,
    spec: TransformationSpec,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: Int? = null,
) {
    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    icon = actionIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
                    label = { Text(actionLabel) },
                )
            }
        }
    }
}
