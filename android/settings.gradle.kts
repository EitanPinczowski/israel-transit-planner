// The Android app. Needs the Android SDK (Android Studio, or CI). The planning engine
// lives in ../core, a plain-Kotlin build that is pulled in here as a composite build,
// so it can be built and tested without the SDK.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "israel-transit-planner"
include(":app")
includeBuild("../core")
