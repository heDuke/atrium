package com.alliehe.atrium.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Native seed extraction (`:core:color-native`, `AndroidBitmap_lockPixels` + NEON) per app icon.
 * Honeycomb tints every cell with the icon's dominant colour, so a cold start runs one
 * `AtriumExtract` trace section per visible icon. Target: Min/Average < 1 ms per section.
 *
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.alliehe.atrium.benchmark.ColorExtractionBenchmark`
 */
@OptIn(ExperimentalMetricApi::class)
class ColorExtractionBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun extractDominantColor() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(
            TraceSectionMetric(SECTION, TraceSectionMetric.Mode.Min),
            TraceSectionMetric(SECTION, TraceSectionMetric.Mode.Average),
            TraceSectionMetric(SECTION, TraceSectionMetric.Mode.Max),
            TraceSectionMetric(SECTION, TraceSectionMetric.Mode.Count),
        ),
        compilationMode = CompilationMode.Partial(),
        // Cold: the in-process accent LruCache is empty, so every visible icon is extracted.
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait(
            Intent(Intent.ACTION_MAIN).apply {
                setPackage(PACKAGE)
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("atrium.benchmark.layout", "Honeycomb")
            },
        )
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 5_000)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.alliehe.atrium"
        /** Must match `NativeSchemeGenerator.TRACE_EXTRACT`. */
        const val SECTION = "AtriumExtract"
    }
}
