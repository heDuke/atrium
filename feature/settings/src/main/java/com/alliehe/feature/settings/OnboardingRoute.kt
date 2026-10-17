package com.alliehe.feature.settings

import com.alliehe.core.data.DefaultLauncherProbe
import com.alliehe.core.data.HomeRoleState

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import com.alliehe.core.ui.LocalMessageHost
import com.alliehe.core.ui.MessageKind
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.core.theme.TransformingContent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEP_WELCOME = 0
private const val STEP_LAYOUT = 1
private const val STEP_DEFAULT = 2

/** OOBE: welcome → layout picker → (set as default, only when the HOME role is requestable). */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    blockExit: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()) {
    val saving by viewModel.savingOnboarding.collectAsStateWithLifecycle()
    val icons by viewModel.onboardingIcons.collectAsStateWithLifecycle()
    ObserveSettingsErrors(viewModel)
    val context = LocalContext.current
    val motion = LocalAtriumMotion.current
    var step by rememberSaveable { mutableStateOf(STEP_WELCOME) }
    var layout by rememberSaveable { mutableStateOf(DrawerLayoutMode.List) }
    val roleAvailable = remember { DefaultLauncherProbe.state(context) == HomeRoleState.Available }
    val finish = { viewModel.completeOnboarding(layout, onFinished) }
    // As HOME, back on the first step must not exit to an empty task; otherwise step back.
    BackHandler(enabled = step > STEP_WELCOME || blockExit) { if (step > STEP_WELCOME) step-- }
    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (motion.fades) {
                fadeIn(enterSpec) togetherWith fadeOut(exitSpec)
            } else {
                fadeIn(snap()) togetherWith fadeOut(snap())
            }
        },
        label = "oobe-step",
    ) { current ->
        when (current) {
            STEP_WELCOME -> WelcomeStep(icons, onNext = { step = STEP_LAYOUT })
            STEP_LAYOUT -> LayoutStep(
                selected = layout,
                onSelect = { layout = it },
                last = !roleAvailable,
                saving = saving,
                onNext = { if (roleAvailable) step = STEP_DEFAULT else finish() },
            )
            else -> DefaultLauncherStep(saving = saving, onDone = finish)
        }
    }
}

@Composable
private fun WelcomeStep(icons: List<Bitmap>, onNext: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = onNext) { Text(stringResource(R.string.onboarding_next)) }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.onboarding_title)) }
            }
            item {
                TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                    PopInIcons(icons)
                }
            }
            listOf(R.string.onboarding_intro, R.string.onboarding_drawer, R.string.onboarding_tile_body).forEach { id ->
                item {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(id), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Icons pop in with staggered M3 spatial springs; PowerSaver → fade only; reduced motion → static. */
@Composable
private fun PopInIcons(icons: List<Bitmap>) {
    val motion = LocalAtriumMotion.current
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val shown = icons.take(5)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            val scale = remember { Animatable(if (motion.decorative) 0f else 1f) }
            val alpha = remember { Animatable(if (motion.fades) 0f else 1f) }
            LaunchedEffect(Unit) {
                delay(index * 70L)
                if (motion.decorative) {
                    coroutineScope {
                        launch { alpha.animateTo(1f, effects) }
                        scale.animateTo(1f, spatial)
                    }
                } else if (motion.fades) {
                    alpha.animateTo(1f, effects)
                }
            }
            Box(
                Modifier.size(32.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) { Image(image, contentDescription = null, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun LayoutStep(
    selected: DrawerLayoutMode,
    onSelect: (DrawerLayoutMode) -> Unit,
    last: Boolean,
    saving: Boolean,
    onNext: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = onNext, enabled = !saving) {
            Text(stringResource(when {
                saving -> R.string.onboarding_saving
                last -> R.string.onboarding_done
                else -> R.string.onboarding_next
            }))
        }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.onboarding_pick_layout)) }
            }
            item(key = "layouts") {
                RadioGroupItem(spec) {
                    DrawerLayoutMode.entries.forEach { mode -> LayoutOption(mode, mode == selected, { onSelect(mode) }) }
                }
            }
        }
    }
}

@Composable
private fun DefaultLauncherStep(saving: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val messages = LocalMessageHost.current
    var state by remember { mutableStateOf(DefaultLauncherProbe.state(context)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state = DefaultLauncherProbe.state(context)
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = onDone, enabled = !saving) {
            Text(stringResource(if (saving) R.string.onboarding_saving else R.string.onboarding_done))
        }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_set_default_launcher)) }
            }
            item {
                if (state == HomeRoleState.Available) {
                    Button(
                        onClick = {
                            val intent = DefaultLauncherProbe.createRequestIntent(context)
                            if (intent == null) {
                                state = DefaultLauncherProbe.state(context)
                            } else try {
                                roleLauncher.launch(intent)
                            } catch (_: ActivityNotFoundException) {
                                messages.show(MessageKind.Info, R.string.settings_home_unavailable)
                            } catch (_: SecurityException) {
                                messages.show(MessageKind.Info, R.string.settings_home_unavailable)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(R.string.settings_set_default_launcher)) },
                        secondaryLabel = { Text(stringResource(R.string.settings_set_default_launcher_secondary)) },
                    )
                } else {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(if (state == HomeRoleState.Held) R.string.settings_home_granted
                            else R.string.settings_home_unavailable), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
