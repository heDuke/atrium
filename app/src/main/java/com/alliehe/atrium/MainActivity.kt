package com.alliehe.atrium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alliehe.atrium.ui.AtriumRoot
import com.alliehe.core.theme.AtriumTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * F7: real MAIN+LAUNCHER activity (no trampoline) so Recents attribution stays correct.
 * Pseudo-launcher only — not system Home; no OEM / power / crown assumptions.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtriumTheme {
                AtriumRoot()
            }
        }
    }
}
