plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.pitest)
}

group = "il.transit"
version = "0.1.0"

// Bytecode for Java 17 (Android's target), built by whatever JDK >= 17 is installed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// The app runs on Android 8 (minSdk 26), whose java.time & co. are the Java 8 API. Compiling
// core's main code against that API turns a Java 9+ call (e.g. Duration.truncatedTo, which
// crashed the API 26 UI tests) into a compile error here instead of a crash on old phones.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
    }
}
tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

dependencies {
    // `api`: these types appear in core's public signatures (MotisClient takes an
    // OkHttpClient, MotisJson is a Json), so the app must see them too.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Routing invariants (Phase 11 T5): the checks every itinerary and every feature front must meet, as
// plain functions that return what they found. `test` asserts them on recordings, `golden` on live
// answers, so a rule is written once. Not a test source set: it holds no tests.
val invariants: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[invariants.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[invariants.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

// Golden trips: the reference trips against LIVE Transitous (~25 requests). A separate
// source set so `test` and CI never run it; see .claude/skills/golden-trips.
val golden: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + invariants.output
    runtimeClasspath += sourceSets.main.get().output + invariants.output
}
configurations[golden.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[golden.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

tasks.register<JavaExec>("goldenTrips") {
    description = "Runs the golden trips against live Transitous and writes build/golden/report.md."
    group = "verification"
    classpath = golden.runtimeClasspath
    mainClass.set("il.transit.core.golden.GoldenTripsKt")
    workingDir = projectDir
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    outputs.upToDateWhen { false }
}

sourceSets.test {
    compileClasspath += invariants.output
    runtimeClasspath += invariants.output
}

tasks.test {
    // Unit tests must never reach the network; fixtures only (see .claude/skills/transitous-api).
    systemProperty("transit.offline", "true")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT }
}

// Style (Phase 11 T2): ktlint reports every finding, `tools/ktlint_gate.py` fails on those that sit
// on lines a PR added or changed. Today's findings are frozen in tools/ktlint-baseline.json.
ktlint {
    version.set("1.5.0")
    ignoreFailures.set(true)
    reporters { reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE) }
}

// Line coverage of core's main code (Phase 11 T1). Generated data is not code under test.
// The gate only ratchets up: a PR may raise `minValue`, never lower it (android-build skill).
kover {
    reports {
        filters {
            excludes {
                classes(
                    "il.transit.core.geo.RailStationsData*",
                    "il.transit.core.calendar.HolidaysData*",
                    "il.transit.core.search.TownsData*",
                    "il.transit.core.search.NeighbourhoodsData*",
                    "il.transit.core.golden.*",
                    "il.transit.core.invariants.*",
                )
            }
        }
        verify {
            rule("core line coverage") {
                bound { minValue.set(98) }
            }
        }
    }
}

// Mutation testing (Phase 11 T5): PIT on the pure packages. Weekly: all of them (mutation.yml); on a PR that
// changes core/src/main: only the changed classes (-PpitClasses=a.B*,c.D*), with the history cached, and
// tools/mutation_score.py fails a class that scores more than 2 points under core/mutation-baseline.json.
// Kotlin's generated null checks (Intrinsics) are not worth killing, so calls to them are not mutated.
pitest {
    pitestVersion.set(libs.versions.pitestEngine.get())
    val packages = listOf("present", "fare", "features", "remind", "ride", "calendar")
    targetClasses.set(
        (findProperty("pitClasses") as String?)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: (packages.map { "il.transit.core.$it.*" } + "il.transit.core.plan.TripPages*"),
    )
    // Generated data tables are not logic.
    excludedClasses.set(listOf("*Data", "*Data$*"))
    targetTests.set(listOf("il.transit.core.*"))
    avoidCallsTo.set(listOf("kotlin.jvm.internal", "kotlin.collections.CollectionsKt"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("XML", "HTML"))
    timestampedReports.set(false)
    timeoutConstInMillis.set(10_000)
    enableDefaultIncrementalAnalysis.set(true)
    historyInputLocation.set(layout.buildDirectory.file("pit-history/history.bin"))
    historyOutputLocation.set(layout.buildDirectory.file("pit-history/history.bin"))
    jvmArgs.set(listOf("-Dtransit.offline=true"))
    failWhenNoMutations.set(false)
}
