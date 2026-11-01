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
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

android {
    namespace = "il.transit.planner"
    // 36 for the Android 16 Live Update APIs (Phase 10 D10). targetSdk stays 35 (owner decision 8):
    // no Android 16 behaviour change rides along.
    compileSdk = 36

    defaultConfig {
        applicationId = "il.transit.planner"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseVersion
        // Real phones are ARM. The x86 copies of MapLibre only serve emulators and roughly
        // halve the APK's size; add "x86_64" back here to test on an emulator.
        // -PsmokeAbi=x86_64 (CI's release-smoke job, Phase 11 T8) adds the emulator's ABI to a release build,
        // so the signed-like release APK runs on the x86_64 emulator without ARM translation. Never set for a release.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") + listOfNotNull(providers.gradleProperty("smokeAbi").orNull) }
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
            // en-XA / ar-XB, for the nightly pseudolocale audit (Phase 11 T7). Debug-only strings.
            isPseudoLocalesEnabled = true
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
    // Android Lint is a gate (Phase 11 T2): errors fail, lint.xml raises a fixed list of warning ids to
    // fatal, and `lint-baseline.xml` freezes today's findings so only new ones fail (android-build skill).
    lint {
        lintConfig = file("lint.xml")
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
        textReport = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Screenshot tests (Paparazzi) render real answers recorded for core's own tests.
    sourceSets["test"].resources.srcDir("../../core/src/test/resources")
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
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    constraints {
        testImplementation(libs.guava.test) { because("GHSA-xxph-c9ww-hj94 in the Guava Paparazzi brings (deps.yml)") }
    }

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(libs.accessibility.test.framework)
    androidTestImplementation(libs.uiautomator)
    // Phase 11 T4: the network-fault matrix's local server (emulator loopback only, never the internet).
    androidTestImplementation(libs.okhttp.mockwebserver)
    // Phase 11 T8 (owner decision 25): leaks fail a journey. LeakCanary lives in the uitest build only (debug and release
    // never see it); AppDriver.close() asks it whether the Activity, ViewModel, MapView or service it just closed is still held.
    "uitestImplementation"(libs.leakcanary.android)
    androidTestImplementation(libs.leakcanary.instrumentation)
}

// Paparazzi 1.3.5 (layoutlib Jellyfish, API 34) copies android.os.Build's inner classes from
// its own copy and fails on API 36's new Build.VERSION_CODES_FULL (NoSuchElementException at
// Renderer.kt, then every shot fails). compileSdk is 36 only for the Live Update APIs (D10), so the
// screenshot tests keep rendering against the API 35 android.jar they used before: same goldens.
// Remove this once Paparazzi is on a release that knows API 36.
// AGP's mockable jar (an "android.jar" under its transforms cache) is rebuilt from the API 35
// platform with AGP's own generator, and takes the same classpath slot.
val mockable35 = layout.buildDirectory.file("mockable/mockable-android-35.jar")
val mockableAndroid35 by tasks.registering {
    val sdk35 = android.sdkDirectory.resolve("platforms/android-35/android.jar")
    inputs.file(sdk35)
    outputs.file(mockable35)
    doLast {
        val out = mockable35.get().asFile
        out.parentFile.mkdirs()
        com.android.builder.testing.MockableJarGenerator(false).createMockableJar(sdk35, out)
    }
}
tasks.withType<Test>().configureEach {
    dependsOn(mockableAndroid35)
    val platformJar = { f: File -> f.name == "android.jar" || (f.name.startsWith("mockable-android") && "35" !in f.name) }
    doFirst {
        val jar = mockable35.get().asFile
        val before = classpath // the old collection, so the new one does not refer to itself
        logger.lifecycle("Paparazzi: ${before.filter(platformJar).files.map { it.path }} -> $jar")
        classpath = files(before.files.map { if (platformJar(it)) jar else it })
    }
}

// Line coverage of the app's ViewModel and state code from the JVM tests (Phase 11 T3, owner decision 21):
// `ui/` without the Compose screens (Paparazzi covers those) and without the classes that only exist to
// talk to Compose, the map and Android (MainScreen, Theme, MapController, ...). The gate only ratchets up
// (android-build skill): a PR may raise `minValue`, never lower it.
kover {
    reports {
        filters {
            includes { classes("il.transit.planner.ui.*") }
            excludes {
                classes(
                    "il.transit.planner.ui.screens.*",
                    "il.transit.planner.ui.MainScreen*",
                    "il.transit.planner.ui.ComposableSingletons*",
                    "il.transit.planner.ui.Theme*",
                    "il.transit.planner.ui.MapController*",
                    "il.transit.planner.ui.OfflineMap*",
                    "il.transit.planner.ui.VoiceSearch*",
                    "il.transit.planner.ui.Shortcuts*",
                    "il.transit.planner.ui.AppLocale*",
                )
            }
        }
        verify {
            rule("ui line coverage") {
                bound { minValue.set(69) }
            }
        }
    }
}

// Style (Phase 11 T2): ktlint reports every finding, `tools/ktlint_gate.py` fails on those that sit
// on lines a PR added or changed. Today's findings are frozen in tools/ktlint-baseline.json.
ktlint {
    version.set("1.5.0")
    android.set(true)
    ignoreFailures.set(true)
    reporters { reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE) }
}
