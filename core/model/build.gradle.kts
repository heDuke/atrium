plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.alliehe.core.model"
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
    // ThemeSource / WearColorRoles live in the pure-Kotlin color module.
    api(project(":core:color"))
    testImplementation(libs.junit)
    testImplementation(libs.icu4j)
}
