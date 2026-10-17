plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure JVM: Wear color-role model, theme-source resolution and the SchemeGenerator seam.
// Consumed by :core:model, :core:theme (Compose) and :feature:tile (ProtoLayout).
// The production seed->palette generator (native, JNI) plugs in behind SchemeGenerator later.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("atrium.updateGolden", (project.findProperty("atrium.updateGolden") ?: "false").toString())
}
