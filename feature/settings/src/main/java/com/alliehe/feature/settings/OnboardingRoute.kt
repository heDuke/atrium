package com.alliehe.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * G2 first-run guide, including how to add the Tile (system path; app provides content only).
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    viewModel.completeOnboarding()
                    onFinished()
                },
            ) {
                Text(stringResource(R.string.onboarding_done))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                BodyText(
                    text = stringResource(R.string.onboarding_intro),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                BodyText(
                    text = stringResource(R.string.onboarding_drawer),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.onboarding_tile_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
            item {
                BodyText(
                    text = stringResource(R.string.onboarding_tile_body),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                BodyText(
                    text = stringResource(R.string.onboarding_power),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}
