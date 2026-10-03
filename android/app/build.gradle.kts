import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Release version comes from the tag (the release workflow sets RELEASE_VERSION=0.2.0).
// versionCode = major*10000 + minor*100 + patch, so every later tag installs over the last.
val releaseVersion: String = System.getenv("RELEASE_VERSION")?.removePrefix("v") ?: "0.0.0-dev"
val releaseCode: Int = releaseVersion.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
    .let { p -> if (p.size == 3 && '-' !in releaseVersion) p[0] * 10000 + p[1] * 100 + p[2] else 1 }

// Signing: CI passes the keystore through environment variables (from GitHub Secrets);
// a local builder can use a git-ignored android/keystore.properties instead. With neither,
// the release APK is built unsigned — enough for CI to prove it compiles.
val localSigning = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: localSigning.getProperty(prop)
val keystorePath = signingValue("RELEASE_KEYSTORE_PATH", "storeFile")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "il.transit.planner"
    compileSdk = 35

    defaultConfig {
        applicationId = "il.transit.planner"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseVersion
        // Real phones are ARM. The x86 copies of MapLibre only serve emulators and roughly
        // halve the APK's size; add "x86_64" back here to test on an emulator.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        // A debug build is signed with a throwaway key. Under the same package id it would
        // block the real release from installing ("App not installed"), so it gets its own.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // The UI-test build: debug plus src/uitest (recorded Transitous answers, a fixed clock,
        // a blank map), so emulators, monkey runs and Test Lab never touch Transitous or
        // OpenFreeMap. Adds x86_64 for the CI emulator; debug and release stay ARM only.
        // See .claude/skills/ui-testing.
        create("uitest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-uitest"
            matchingFallbacks += listOf("debug")
            ndk { abiFilters += listOf("x86_64") }
        }
        release {
            // R8 off on purpose: MapLibre and kotlinx-serialization need keep rules, and a
            // few MB saved is not worth a release that crashes on reflection.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    testBuildType = "uitest"
    // The recorded answers live in core's test resources; the uitest build ships them as assets.
    sourceSets.maybeCreate("uitest").assets.srcDir("../../core/src/test/resources")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/*.kotlin_module")
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation("il.transit:core") // substituted by includeBuild("../core")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.maplibre.android)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(libs.uiautomator)
}
