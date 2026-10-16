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
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alliehe.atrium.theme.ThemeSeedProvider
import com.alliehe.atrium.theme.ThemeViewModel
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
    private var homeRequest by mutableIntStateOf(0)
    private var homeSession by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBenchmarkHooks()
        homeSession = savedInstanceState?.getBoolean(HOME_SESSION) ?: intent.isHomeRequest()
        enableEdgeToEdge()
        setContent {
            val themePrefs by themeViewModel.themePrefs.collectAsStateWithLifecycle()
            val seedArgb = ThemeSeedProvider.resolveSeedArgb(themePrefs.seedColorArgb)
            AtriumTheme(
                performanceMode = themePrefs.performanceMode,
                reduceMotion = themePrefs.reduceMotion,
                seedColor = seedArgb?.let { Color(it.toInt()) },
            ) {
                AtriumRoot(homeRequest = homeRequest, homeSession = homeSession)
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
