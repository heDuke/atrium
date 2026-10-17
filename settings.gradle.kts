pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Atrium"

include(
    ":app",
    ":core:color",
    ":core:color-native",
    ":core:model",
    ":core:ui",
    ":core:data",
    ":core:theme",
    ":core:navigation",
    ":feature:drawer",
    ":feature:settings",
    ":feature:tile",
    ":feature:watchface",
    ":benchmark",
)
