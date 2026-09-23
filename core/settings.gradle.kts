// core/ is a standalone build: pure Kotlin/JVM, no Android SDK needed.
// android/ pulls it in with includeBuild("../core").
rootProject.name = "core"

dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
