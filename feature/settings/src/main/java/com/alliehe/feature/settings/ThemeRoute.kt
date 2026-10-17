package com.alliehe.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.color.BrandColors
import com.alliehe.core.color.ThemeSource
import com.alliehe.core.theme.LocalSchemeGenerator
import com.alliehe.core.theme.LocalThemeResolution
import com.alliehe.core.ui.SectionHeader
import com.alliehe.core.ui.R as UiR

private const val PRESETS_PER_ROW = 4

/**
 * Settings → Theme: colour source (System / Seed / Brand), 8 presets, themed icons, and a live
 * preview. Choices apply to the whole app immediately (and to the tile on its next refresh), so the
 * preview card simply renders with the current theme.
 */
@Composable
fun ThemeRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val prefsState by viewModel.prefs.collectAsStateWithLifecycle()
    val prefs = prefsState ?: return
    ObserveSettingsErrors(viewModel)
    val resolution = LocalThemeResolution.current
    val generator = LocalSchemeGenerator.current
    // Each preset's swatch shows its generated primary (what the UI accent will actually be).
    val swatches = remember(generator) { BrandColors.PRESETS.map { Color(generator.generate(it).primary) } }
    val selectedSeed = prefs.seedColorArgb?.toInt()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_theme)) }
            }
            item(key = "preview") {
                Card(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        val cs = MaterialTheme.colorScheme
                        listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer, cs.surfaceContainerHigh).forEach {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(it))
                        }
                    }
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.theme_preview_primary)) })
                    Button(onClick = {}, colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        label = { Text(stringResource(R.string.theme_preview_tonal)) })
                }
            }
            if (resolution.systemUnavailable) {
                item(key = "unavailable") {
                    Text(stringResource(R.string.theme_system_unavailable), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec))
                }
            }
            item(key = "h-source") { SectionHeader(stringResource(R.string.theme_source), spec) }
            item(key = "sources") {
                RadioGroupItem(spec) {
                    listOf(
                        Triple(ThemeSource.System, R.string.theme_source_system, R.string.theme_source_system_secondary),
                        Triple(ThemeSource.Seed, R.string.theme_source_seed, R.string.theme_source_seed_secondary),
                        Triple(ThemeSource.Brand, R.string.theme_source_brand, null),
                    ).forEach { (source, label, secondary) ->
                        RadioButton(selected = prefs.themeSource == source,
                            onSelect = { viewModel.setThemeSource(source) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(label)) },
                            secondaryLabel = secondary?.let { { Text(stringResource(it)) } })
                    }
                }
            }
            item(key = "h-presets") { SectionHeader(stringResource(R.string.theme_presets), spec) }
            item(key = "presets") {
                Column(Modifier.fillMaxWidth().transformedHeight(this, spec).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandColors.PRESETS.withIndex().chunked(PRESETS_PER_ROW).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { (index, seed) ->
                                val selected = prefs.themeSource == ThemeSource.Seed && selectedSeed == seed
                                val name = if (seed == BrandColors.BRAND_SEED) stringResource(R.string.theme_preset_brand)
                                    else stringResource(R.string.theme_preset_n, index)
                                PresetSwatch(swatches[index], selected, name) { viewModel.selectSeed(seed) }
                            }
                        }
                    }
                }
            }
            item(key = "themed-icons") {
                SwitchButton(checked = prefs.themedIcons, onCheckedChange = viewModel::setThemedIcons,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.theme_themed_icons)) },
                    secondaryLabel = { Text(stringResource(R.string.theme_themed_icons_secondary)) })
            }
        }
    }
}

/** 48dp touch target around a 36dp swatch; selected = outline ring + check. */
@Composable
private fun PresetSwatch(color: Color, selected: Boolean, name: String, onSelect: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color)
                .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(UiR.drawable.ic_atrium_check), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
