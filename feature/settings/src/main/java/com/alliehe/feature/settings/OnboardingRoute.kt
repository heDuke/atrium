package com.alliehe.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.theme.TransformingContent

@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val saving by viewModel.savingOnboarding.collectAsStateWithLifecycle()
    ObserveSettingsErrors(viewModel)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = { viewModel.completeOnboarding(onFinished) }, enabled = !saving) {
            Text(stringResource(if (saving) R.string.onboarding_saving else R.string.onboarding_done))
        }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.onboarding_title))
                }
            }
            val paragraphs = listOf(R.string.onboarding_intro, R.string.onboarding_drawer,
                R.string.onboarding_tile_body, R.string.onboarding_power)
            paragraphs.forEach { textId ->
                item {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(textId), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
