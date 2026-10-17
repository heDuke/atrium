plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.alliehe.atrium.benchmark"
    compileSdk = 37
    // Same NDK as :core:color-native so release packaging can strip libatrium_color.so.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 33
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Only the benchmark variant is meaningful; drop debug/release to keep CI fast.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
}
