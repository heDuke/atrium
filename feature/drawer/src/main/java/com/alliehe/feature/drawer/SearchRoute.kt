package com.alliehe.feature.drawer

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.TransformingContent

private const val SEARCH_INPUT_KEY = "search_query"

@Composable
fun SearchRoute(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val queryHint = stringResource(R.string.search_query_hint)
    val inputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(SEARCH_INPUT_KEY) }
                ?.let { viewModel.onQueryChange(it.toString()) }
        }
    }
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.drawer_search_title))
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent,
                            listOf(RemoteInput.Builder(SEARCH_INPUT_KEY).setLabel(queryHint).build()))
                        try {
                            inputLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.search_input_unavailable, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(context, R.string.search_input_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(uiState.query.ifBlank { queryHint }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(R.string.search_edit_hint)) },
                )
            }
            if (uiState.query.isNotEmpty()) {
                item {
                    Button(onClick = { viewModel.onQueryChange("") },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec)) {
                        Text(stringResource(R.string.search_clear))
                    }
                }
            }
            if (uiState.refreshFailed) {
                item {
                    Button(onClick = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.catalog_retry)) }
                }
            }
            if (uiState.loading || uiState.results.isEmpty()) {
                item {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(when {
                            uiState.loading -> R.string.catalog_loading
                            uiState.query.isBlank() -> R.string.drawer_empty_hint
                            else -> R.string.search_no_results
                        }), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else items(uiState.results, key = { it.componentKey }) { entry ->
                Button(
                    onClick = {
                        if (AppLauncher.launch(context, entry) != LaunchResult.Started) {
                            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                            viewModel.refresh()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { AppIcon(uiState.icons[entry.componentKey], entry.label) },
                    label = { Text(entry.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}
