plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "il.transit"
version = "0.1.0"

// Bytecode for Java 17 (Android's target), built by whatever JDK >= 17 is installed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    // `api`: these types appear in core's public signatures (MotisClient takes an
    // OkHttpClient, MotisJson is a Json), so the app must see them too.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Golden trips: the reference trips against LIVE Transitous (~25 requests). A separate
// source set so `test` and CI never run it; see .claude/skills/golden-trips.
val golden: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
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

tasks.test {
    // Unit tests must never reach the network; fixtures only (see .claude/skills/transitous-api).
    systemProperty("transit.offline", "true")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT }
}
