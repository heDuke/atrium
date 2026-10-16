package com.alliehe.atrium.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private const val PACKAGE = "com.alliehe.atrium"
private const val EXTRA_LAYOUT = "atrium.benchmark.layout"
private const val ITERATIONS = 5

/**
 * Scroll + launch jank per drawer layout. Run on a Wear OS device/emulator:
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest`
 * The app's `benchmark` build type honours [EXTRA_LAYOUT] (skips OOBE, selects the layout).
 */
@RunWith(Parameterized::class)
class DrawerBenchmarks(private val layout: String) {
    @get:Rule val rule = MacrobenchmarkRule()

    /** Cold start into the drawer: startup time + first-frames jank. */
    @Test fun startup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        pressHome()
        startDrawer()
    }

    /** Scroll (List/Grid) or pan (Honeycomb) jank. */
    @Test fun scroll() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = { startDrawer() },
    ) {
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(3) {
            if (layout == "Honeycomb") {
                device.swipe(w * 3 / 4, h / 2, w / 4, h / 2, 10)
                device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 10)
            } else {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
                    ?: device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 10)
            }
            device.waitForIdle()
        }
    }

    /** Tap an app icon: measures the frames of the scale-up launch transition out of the drawer. */
    @Test fun launchApp() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = { startDrawer() },
    ) {
        // Second row below the search button is reliably an app in every layout.
        device.click(device.displayWidth / 2, device.displayHeight * 5 / 8)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.startDrawer() {
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                setPackage(PACKAGE)
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(EXTRA_LAYOUT, layout)
            },
        )
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 5_000)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun layouts() = listOf("List", "Grid", "Honeycomb")
    }
}
