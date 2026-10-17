package com.alliehe.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * One TLC item holding a single radio group, so `selectableGroup()` scopes exactly that group
 * (TalkBack announces "n of m" per group instead of across the whole settings list).
 */
@Composable
internal fun TransformingLazyColumnItemScope.RadioGroupItem(
    spec: TransformationSpec,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}
