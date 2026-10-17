package com.alliehe.atrium

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.tiles.TileService
import com.alliehe.atrium.theme.ThemeViewModel
import com.alliehe.core.color.nativeimpl.NativeSchemeGenerator
import com.alliehe.core.data.AppIconLoader
import com.alliehe.core.data.IconStyle
import com.alliehe.core.ui.LocalAppIconLoader
import com.alliehe.core.ui.LocalIconStyle
import com.alliehe.feature.tile.AtriumTileService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.alliehe.atrium.ui.AtriumRoot
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.DrawerLayoutMode
import kotlinx.coroutines.runBlocking
import com.alliehe.core.theme.AtriumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    @Inject lateinit var installedAppsRepository: InstalledAppsRepository
    @Inject lateinit var userPrefsRepository: UserPrefsRepository
    @Inject lateinit var schemeGenerator: NativeSchemeGenerator
    @Inject lateinit var iconLoader: AppIconLoader
    private var homeRequest by mutableIntStateOf(0)
    private var homeSession by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBenchmarkHooks()
        homeSession = savedInstanceState?.getBoolean(HOME_SESSION) ?: intent.isHomeRequest()
        enableEdgeToEdge()
        requestTileUpdateOnThemeChange()
        setContent {
            val themePrefs by themeViewModel.themePrefs.collectAsStateWithLifecycle()
            AtriumTheme(
                performanceMode = themePrefs.performanceMode,
                reduceMotion = themePrefs.reduceMotion,
                themeSource = themePrefs.themeSource,
                userSeedArgb = themePrefs.userSeedArgb,
                watchFaceSeedArgb = themePrefs.watchFaceSeedArgb,
                generator = schemeGenerator,
            ) {
                val colors = MaterialTheme.colorScheme
                val iconStyle = remember(themePrefs.themedIcons, colors) {
                    if (!themePrefs.themedIcons) IconStyle.Default
                    else IconStyle(themed = true, foreground = colors.onPrimaryContainer.toArgb(),
                        container = colors.primaryContainer.toArgb())
                }
                CompositionLocalProvider(LocalAppIconLoader provides iconLoader, LocalIconStyle provides iconStyle) {
                    AtriumRoot(homeRequest = homeRequest, homeSession = homeSession)
                }
            }
        }
    }

    /** The tile renders with the same theme source/seed; ask System UI to refresh it when they change. */
    private fun requestTileUpdateOnThemeChange() {
        lifecycleScope.launch {
            userPrefsRepository.prefs.map { it.themeSource to it.seedColorArgb }.distinctUntilChanged().drop(1).collect {
                runCatching { TileService.getUpdater(this@MainActivity).requestUpdate(AtriumTileService::class.java) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        installedAppsRepository.refreshIfStale()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A Tile or normal launcher entry should also open the drawer, but may exit normally.
        homeSession = intent.isHomeRequest()
        homeRequest++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(HOME_SESSION, homeSession)
        super.onSaveInstanceState(outState)
    }

    /** :benchmark only (BENCHMARK_HOOKS is false in debug/release): skip OOBE and pick a layout. */
    private fun applyBenchmarkHooks() {
        if (!BuildConfig.BENCHMARK_HOOKS) return
        val layout = intent.getStringExtra(EXTRA_BENCHMARK_LAYOUT)
            ?.let { name -> DrawerLayoutMode.entries.firstOrNull { it.name == name } } ?: return
        runBlocking { userPrefsRepository.completeOnboarding(layout) }
    }

    private fun Intent.isHomeRequest(): Boolean =
        action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_HOME)

    private companion object {
        const val HOME_SESSION = "home_session"
        const val EXTRA_BENCHMARK_LAYOUT = "atrium.benchmark.layout"
    }
}
