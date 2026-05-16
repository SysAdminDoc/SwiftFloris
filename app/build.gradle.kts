/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutlibraries)
    alias(libs.plugins.kotest)
    alias(libs.plugins.kotlinx.kover)
    // Roborazzi Gradle plugin is deliberately *not* applied here: the
    // current 1.43.x release line uses the AGP `TestedExtension` API
    // that AGP 9.0.0 removed, so applying the plugin fails the build.
    // The capture API (`captureRoboImage`) still works as a pure
    // testImplementation dependency — Roborazzi tasks (`recordRoborazzi`,
    // `verifyRoborazzi`) can be invoked manually via the standard JUnit
    // entry points, and the plugin can be re-enabled once Roborazzi
    // ships an AGP-9-compatible release (1.44.0-stable expected to land
    // the new variant API).
    // alias(libs.plugins.roborazzi)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project
val projectVersionNameSuffix = projectVersionName.substringAfter("-", "").let { suffix ->
    if (suffix.isNotEmpty()) {
        "-$suffix"
    } else {
        suffix
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-jvm-default=enable",
            "-Xwhen-guards",
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
            "-XXLanguage:+LocalTypeAliases",
        ))
    }
}

configure<ApplicationExtension> {
    namespace = "dev.patrickgold.florisboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    ndkVersion = tools.versions.ndk.get()

    // ROADMAP §6 N6.2 — release signing. The KEYSTORE_PATH + SIGNING_*
    // env vars are populated by the GitHub release workflow from encrypted
    // secrets. When KEYSTORE_PATH is unset (local builds, fork dispatches
    // without secrets), the release variant falls back to debug signing
    // so contributors can still validate the build pipeline.
    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        applicationId = "dev.patrickgold.florisboard"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v~draft2\"")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"beta.addons.florisboard.org\"")

        sourceSets {
            maybeCreate("main").apply {
                assets.directories += "src/main/assets"
            }
        }
    }

    bundle {
        language {
            // We disable language split because FlorisBoard does not use
            // runtime Google Play Service APIs and thus cannot dynamically
            // request to download the language resources for a specific locale.
            enableSplit = false
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug+${getGitCommitHash(short = true).get()}"

            isDebuggable = true
            isJniDebuggable = false
        }

        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = projectVersionNameSuffix

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true
        }

        named("release") {
            versionNameSuffix = projectVersionNameSuffix

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true

            // Use the release signing config when the env-driven keystore is present;
            // otherwise fall back to debug signing so the build still produces an APK.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }

        create("benchmark") {
            initWith(getByName("release"))

            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench+${getGitCommitHash(short = true).get()}"

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    lint {
        baseline = file("lint.xml")
        disable.addAll(
            listOf(
                "UElementAsPsi",
            )
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // ROADMAP §7 Next-12.1 — production code calls `android.os.Trace`
            // for Macrobenchmark/Perfetto instrumentation. The Android JVM
            // stubs throw "Method not mocked" by default; flipping
            // returnDefaultValues lets `Trace.beginSection` / `endSection`
            // return their stub defaults so unit tests don't trip on tracing.
            isReturnDefaultValues = true
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

aboutLibraries {
    collect {
        configPath = file("src/main/config")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

tasks.withType<Test> {
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
    }
    useJUnitPlatform()
}

kover {
    useJacoco()
}

// ROADMAP §6 N7.1 — Pin the no-network promise in code, not just in marketing.
// Fails the build if any AndroidManifest.xml in the :app sourceSets declares the
// INTERNET permission (or any equivalent network-permission alias). The check
// runs as part of preBuild on every variant, so PRs cannot accidentally break
// the offline-only contract that makes SwiftFloris viable for F-Droid privacy
// review. Removing this check is itself a load-bearing review signal.
val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Fails the build if any AndroidManifest.xml declares INTERNET / network permissions (ROADMAP §6 N7.1)."

    val manifests = fileTree("src") {
        include("**/AndroidManifest.xml")
    }
    inputs.files(manifests).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { true }

    val bannedPermissions = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
    )

    doLast {
        val violations = mutableListOf<String>()
        manifests.forEach { manifest ->
            val text = manifest.readText()
            bannedPermissions.forEach { perm ->
                val pattern = Regex(
                    """<uses-permission[^>]*android:name\s*=\s*"${Regex.escape(perm)}""""
                )
                if (pattern.containsMatchIn(text)) {
                    violations += "${manifest.relativeTo(projectDir)} declares $perm"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("SwiftFloris no-network contract violation (ROADMAP §6 N7.1):")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    append("SwiftFloris must ship without ANY network permission. ")
                    append("If a feature genuinely needs network access, it must move to ")
                    append("an isolated optional module loaded by user opt-in, never the base APK.")
                }
            )
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure {
        dependsOn(verifyNoInternetPermission)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // testImplementation(composeBom)
    // androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.exifinterface)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.window.core)
    implementation(libs.cache4k)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.model)
    ksp(libs.patrickgold.jetpref.datastore.model.processor)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)
    implementation(libs.sqlcipher.android)
    implementation(libs.zxing.core)

    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.compose)
    implementation(projects.lib.kotlin)
    //implementation(projects.lib.native)  // Rust native - optional for keyboard core
    implementation(projects.lib.snygg)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // ROADMAP §7 Next-12.2 — Roborazzi Compose screenshot regression suite.
    // Robolectric-backed so the snapshots run on the JVM (no device, no
    // emulator), which makes them cheap to run on every CI pull request.
    // junit-vintage-engine lets the JUnit-4-style Robolectric tests run
    // under the project-wide `useJUnitPlatform()` runner alongside the
    // Kotest 5 suites.
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.1")
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!File(".git").exists()) {
        return providers.provider { "null" }
    }

    val execProvider = providers.exec {
        if (short) {
            commandLine("git", "rev-parse", "--short", "HEAD")
        } else {
            commandLine("git", "rev-parse", "HEAD")
        }
    }
    return execProvider.standardOutput.asText.map { it.trim() }
}
