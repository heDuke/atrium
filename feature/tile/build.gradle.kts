plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.alliehe.feature.tile"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material3)
    implementation(libs.protolayout.expression)
    implementation(libs.guava)
}
