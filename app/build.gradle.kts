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
    // Roborazzi 1.55.0 (Jan 2026 line) ships AGP-9 support via PR #782,
    // so the Gradle plugin is now applied. This lights up the
    // `:app:recordRoborazziDebug` (baseline capture),
    // `:app:verifyRoborazziDebug` (PR/push regression verify), and the
    // `:app:verifyRoborazziRelease` alias backed by the non-shipping
    // releaseRoborazzi variant so the release workflow can run the same
    // baselines against release resources/build flags before publishing.
    // Baseline images live under
    // `app/src/test/snapshots/images/` per Roborazzi convention.
    alias(libs.plugins.roborazzi)
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
        // RESEARCH_FEATURE_PLAN.md F14 — compile-time "What's new" excerpt sourced
        // from the matching CHANGELOG.md section so Settings → About can show it
        // offline (no INTERNET, no runtime file IO). Empty when no section matches.
        buildConfigField(
            "String",
            "WHATS_NEW",
            "\"${whatsNewExcerpt(projectVersionName.substringBefore("-")).escapeForBuildConfig()}\"",
        )

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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

        create("releaseRoborazzi") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")

            // F24: non-shipping release-equivalent variant used only by
            // :app:verifyRoborazziRelease. Its source-set overlay declares the
            // screenshot host activity that Robolectric needs; the real release
            // APK manifest stays untouched.
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
        lintConfig = file("lint.xml")
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
// Fails the build if INTERNET / network permissions appear in either the :app
// source manifests OR the variant's merged manifest (which folds in every
// library AAR and every flavor/buildType overlay). A library that tries to
// re-add INTERNET via manifest-merging is caught by the merged-manifest check.
//
// Legitimate `tools:node="remove"` directives (used to strip a permission a
// library erroneously declares) are exempted in both checks — the source-
// manifest pre-check skips them, and the merged-manifest post-check sees only
// the post-merge result where the removal already took effect.
//
// The source-manifest pre-check runs as part of preBuild for fast feedback;
// the merged-manifest post-check is wired per-variant against AGP's
// MERGED_MANIFEST artifact so PRs cannot accidentally break the offline-only
// contract that makes SwiftFloris viable for F-Droid privacy review.
// Removing either check is itself a load-bearing review signal.
val bannedNetworkPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_STATE",
)

// Match a <uses-permission ...> element that declares one of the banned names
// AND does NOT carry tools:node="remove" / "removeAll". Multi-line tolerant.
fun findBannedPermissionViolations(manifestText: String): List<String> {
    val usesPermissionPattern = Regex(
        """<uses-permission\b[^>]*?/>|<uses-permission\b[^>]*?>.*?</uses-permission>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    val namePattern = Regex("""android:name\s*=\s*"([^"]+)"""")
    val toolsNodeRemovePattern = Regex(
        """tools:node\s*=\s*"(remove|removeAll)"""",
        RegexOption.IGNORE_CASE,
    )
    val violations = mutableListOf<String>()
    for (match in usesPermissionPattern.findAll(manifestText)) {
        val element = match.value
        if (toolsNodeRemovePattern.containsMatchIn(element)) continue
        val name = namePattern.find(element)?.groupValues?.getOrNull(1) ?: continue
        if (name in bannedNetworkPermissions) {
            violations += name
        }
    }
    return violations
}

val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Fails the build if any source AndroidManifest.xml declares INTERNET / network permissions (ROADMAP §6 N7.1)."

    val manifests = fileTree("src") {
        include("**/AndroidManifest.xml")
    }
    inputs.files(manifests).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { true }

    doLast {
        val violations = mutableListOf<String>()
        manifests.forEach { manifest ->
            val text = manifest.readText()
            findBannedPermissionViolations(text).forEach { perm ->
                violations += "${manifest.relativeTo(projectDir)} declares $perm"
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

// Merged-manifest check: scans the post-merge AndroidManifest.xml for each
// variant, so an AAR / library that tries to add INTERNET via manifest merging
// is caught. Wired against AGP's SingleArtifact.MERGED_MANIFEST so the task
// runs after the manifest merger and before assemble.
androidComponents {
    beforeVariants(selector().withBuildType("releaseRoborazzi")) { variantBuilder ->
        // F24: AGP only creates the debug unit-test component by default for
        // application modules. Roborazzi wires tasks from AGP UnitTest
        // components, so explicitly enable unit tests on the non-shipping
        // releaseRoborazzi variant and expose it through the stable
        // :app:verifyRoborazziRelease alias below.
        (variantBuilder as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }

    onVariants { variant ->
        val verifyMerged = tasks.register("verifyNoInternetPermissionMerged${variant.name.replaceFirstChar { it.uppercase() }}") {
            group = "verification"
            description = "Fails the build if the merged AndroidManifest for variant ${variant.name} declares INTERNET / network permissions (ROADMAP §6 N7.1)."

            val mergedManifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
            inputs.file(mergedManifest).withPathSensitivity(PathSensitivity.RELATIVE)
            outputs.upToDateWhen { true }

            val variantName = variant.name

            doLast {
                val file = mergedManifest.get().asFile
                if (!file.exists()) {
                    throw GradleException("verifyNoInternetPermissionMerged: merged manifest missing for $variantName at $file")
                }
                val text = file.readText()
                val violations = findBannedPermissionViolations(text)
                if (violations.isNotEmpty()) {
                    throw GradleException(
                        buildString {
                            appendLine("SwiftFloris no-network contract violation in MERGED manifest for $variantName (ROADMAP §6 N7.1):")
                            violations.distinct().forEach { appendLine("  - $it") }
                            appendLine()
                            append("A library / AAR re-introduced a banned permission via manifest merging. ")
                            append("If the addition is from an unwanted dependency, strip it with ")
                            append("""<uses-permission android:name="..." tools:node="remove" />""")
                            append(" in app/src/main/AndroidManifest.xml after confirming the library does not actually require network access.")
                        }
                    )
                }
            }
        }
        afterEvaluate {
            val processTaskName = "process${variant.name.replaceFirstChar { it.uppercase() }}Manifest"
            tasks.findByName(processTaskName)?.finalizedBy(verifyMerged)
            tasks.findByName("assemble${variant.name.replaceFirstChar { it.uppercase() }}")?.dependsOn(verifyMerged)
        }
    }
}

tasks.register("verifyRoborazziRelease") {
    group = "verification"
    description = "Runs Roborazzi against the non-shipping releaseRoborazzi variant, which mirrors release build flags and carries only the test host overlay (F24)."
    dependsOn("verifyRoborazziReleaseRoborazzi")
}

// ROADMAP §6 N7.4 — pin the load-bearing excludes in
// `app/src/main/res/xml/data_extraction_rules.xml` against accidental
// rewrite. Android Lint validates the XML against the data-extraction-rules
// schema, but it does NOT check that the file contains exclude entries for
// the SQLCipher personal-dictionary DB, the Tink-wrapped passphrase prefs,
// or the clipboard-history directory. Without those specific excludes the
// Android 12+ D2D transfer leak that v1.8.85 closed comes back the moment
// someone "cleans up" the rules file. This task fails the build if any of
// the required excludes are missing from either rule set
// (`<cloud-backup>` and `<device-transfer>`).
val verifyDataExtractionRules = tasks.register("verifyDataExtractionRules") {
    group = "verification"
    description = "Fails the build if data_extraction_rules.xml drops a load-bearing exclude (ROADMAP §6 N7.4)."

    val rulesFile = file("src/main/res/xml/data_extraction_rules.xml")
    inputs.file(rulesFile).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { true }

    // Each entry is a *substring* search on the rules file. The substrings
    // are stable identifiers (file names / pref names / directory names)
    // that any future edit must preserve. We deliberately don't parse the
    // XML — the substring check is cheaper and catches both schema-valid
    // rewrites that drop an exclude AND accidental typos in the file name.
    val requiredExcludes = listOf(
        // SQLCipher personal-dictionary DB + sidecars
        "floris_user_dictionary",
        "floris_user_dictionary.db",
        "floris_user_dictionary.db-journal",
        "floris_user_dictionary.db-wal",
        "floris_user_dictionary.db-shm",
        // Tink-wrapped passphrase prefs
        "floris_user_dictionary_key.xml",
        // Clipboard history dir
        "clipboard_history",
    )
    val requiredSections = listOf("<cloud-backup>", "<device-transfer>")

    doLast {
        if (!rulesFile.exists()) {
            throw GradleException(
                "data_extraction_rules.xml missing at ${rulesFile.path} — this file is " +
                    "load-bearing for the Android 12+ no-leak-via-D2D contract (ROADMAP §6 N7.4)."
            )
        }
        val text = rulesFile.readText()
        val missingSections = requiredSections.filterNot { it in text }
        val missingExcludes = requiredExcludes.filterNot { it in text }
        if (missingSections.isNotEmpty() || missingExcludes.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("data_extraction_rules.xml is missing required content (ROADMAP §6 N7.4):")
                    if (missingSections.isNotEmpty()) {
                        appendLine("  missing rule sections:")
                        missingSections.forEach { appendLine("    - $it") }
                    }
                    if (missingExcludes.isNotEmpty()) {
                        appendLine("  missing exclude identifiers:")
                        missingExcludes.forEach { appendLine("    - $it") }
                    }
                    appendLine()
                    append("Each identifier above MUST appear inside both <cloud-backup> and ")
                    append("<device-transfer> as the `path=` attribute of an <exclude> element. ")
                    append("Without these excludes, Android 12+ D2D transfer carries the SQLCipher ")
                    append("personal-dictionary DB and its undecryptable Tink-wrapped passphrase ")
                    append("to a new device, leaking PII ciphertext and bricking the dictionary on ")
                    append("the new install.")
                }
            )
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure {
        dependsOn(verifyDataExtractionRules)
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
    implementation(libs.tink.android)
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
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!rootProject.file(".git").exists()) {
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

// RESEARCH_FEATURE_PLAN.md F14 — extract the body of the `## v<versionName>`
// section from the repo-root CHANGELOG.md, lightly de-markdown it, and truncate
// to [maxChars] so it can ship as a BuildConfig string for the offline
// "What's new" surface. Returns "" when the section is absent (e.g. a dev build
// between releases), in which case the Settings entry hides itself.
fun whatsNewExcerpt(versionName: String, maxChars: Int = 900): String {
    val changelog = rootProject.file("CHANGELOG.md")
    if (!changelog.exists()) return ""
    val text = changelog.readText()
    val startMarker = "## v$versionName"
    val startIdx = text.indexOf(startMarker)
    if (startIdx < 0) return ""
    val bodyStart = text.indexOf('\n', startIdx).let { if (it < 0) return "" else it + 1 }
    val nextAnchor = text.indexOf("\n<a id=\"v", bodyStart)
    val nextHeader = text.indexOf("\n## v", bodyStart)
    val end = listOf(nextAnchor, nextHeader).filter { it >= 0 }.minOrNull() ?: text.length
    var body = text.substring(bodyStart, end).trim()
        .replace(Regex("(?m)^#{1,6}\\s*"), "") // strip heading hashes
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // unbold
        .replace("`", "") // drop inline-code ticks
        .replace(Regex("\n{3,}"), "\n\n") // collapse blank runs
        .trim()
    if (body.length > maxChars) {
        body = body.substring(0, maxChars).substringBeforeLast('\n').trimEnd() + "\n…"
    }
    return body
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
