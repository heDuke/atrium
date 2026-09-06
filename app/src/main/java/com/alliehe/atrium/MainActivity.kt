package com.alliehe.atrium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alliehe.atrium.theme.ThemeSeedProvider
import com.alliehe.atrium.ui.AtriumRoot
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.UserPrefs
import com.alliehe.core.theme.AtriumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * F7: real MAIN+LAUNCHER activity (no trampoline) so Recents attribution stays correct.
 * Pseudo-launcher only — not system Home; no OEM / power / crown assumptions.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPrefsRepository: UserPrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by userPrefsRepository.prefs.collectAsStateWithLifecycle(
                initialValue = UserPrefs(),
            )
            val seedArgb = ThemeSeedProvider.resolveSeedArgb(prefs.seedColorArgb)
            AtriumTheme(
                performanceMode = prefs.effectivePerformanceMode,
                seedColor = seedArgb?.let { Color(it.toInt()) },
            ) {
                AtriumRoot()
            }
        }
    }
}
