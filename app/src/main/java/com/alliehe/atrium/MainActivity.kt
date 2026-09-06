package com.alliehe.atrium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alliehe.atrium.theme.ThemeViewModel
import com.alliehe.atrium.ui.AtriumRoot
import com.alliehe.core.theme.AtriumTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * F7: real MAIN+LAUNCHER activity (no trampoline) so Recents attribution stays correct.
 * Pseudo-launcher only — not system Home; no OEM / power / crown assumptions.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePrefs by themeViewModel.themePrefs.collectAsStateWithLifecycle()
            val seedColor = themePrefs.seedColorArgb?.let { Color(it.toInt()) }
            AtriumTheme(
                performanceMode = themePrefs.performanceMode,
                seedColor = seedColor,
            ) {
                AtriumRoot()
            }
        }
    }
}
