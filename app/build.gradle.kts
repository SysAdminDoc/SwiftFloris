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
import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import groovy.json.JsonSlurper
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
    // Roborazzi 1.64.0 keeps the AGP-9-compatible plugin line and adds
    // annotation-filtered Compose preview test generation. This lights up the
    // `:app:recordRoborazziDebug` (baseline capture),
    // `:app:verifyRoborazziDebug` (PR/push regression verify), and the
    // `:app:verifyRoborazziRelease` alias backed by the non-shipping
    // releaseRoborazzi variant so local release evidence covers both
    // hand-written screenshots and opt-in @RoboPreviewInclude previews before
    // publishing.
    // Baseline images live under
    // `app/src/test/snapshots/images/` per Roborazzi convention.
    alias(libs.plugins.roborazzi)
}

val projectMinSdk = providers.gradleProperty("projectMinSdk").get()
val projectTargetSdk = providers.gradleProperty("projectTargetSdk").get()
val projectCompileSdk = providers.gradleProperty("projectCompileSdk").get()
val projectVersionCode = providers.gradleProperty("projectVersionCode").get()
val projectVersionName = providers.gradleProperty("projectVersionName").get()
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
        // Kotlin 2.4.x defaults moduleName to "{group}:{project}" which contains colons;
        // KSP 2.3.9 chokes on colons in generated identifiers (ksp#2964). Remove when KSP 2.4.x ships.
        moduleName.set("swiftfloris_app")
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-jvm-default=enable",
            "-XXLanguage:+LocalTypeAliases",
        ))
    }
}

configure<ApplicationExtension> {
    namespace = "dev.patrickgold.florisboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    // SwiftFloris has no native sources — no CMakeLists.txt, no Cargo.toml, no
    // jniLibs, no externalNativeBuild — and `:app:assembleDebug` succeeds with
    // this line removed. It is pinned anyway because `gradle/tools.versions.toml`
    // is the single input list the reproducible-build image is built from
    // (`utils/repr_build/Dockerfile` installs `ndk;${NDK_VERSION}` and
    // `cmake;${CMAKE_VERSION}` from these same properties), and a verifier
    // reproducing a release must land on the same SDK component set the
    // maintainer used. Pinning it here keeps the local toolchain and the
    // container from silently diverging.
    //
    // If the reproducible-build image ever stops installing the NDK, drop this
    // line with it — but do not drop it alone, and confirm the release APK is
    // byte-identical across a clean build either way before doing so.
    ndkVersion = tools.versions.ndk.get()

    // ROADMAP §6 N6.2 — release signing. The KEYSTORE_PATH + SIGNING_*
    // env vars are populated by the maintainer's local release environment.
    // When KEYSTORE_PATH is unset (developer builds or test dispatches without
    // signing material), the release variant falls back to debug signing
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
        // ROADMAP P1 (2026-06-11) — SwiftFloris-owned application ID
        // (io.github.* per F-Droid inclusion policy for GitHub-hosted
        // projects without a controlled domain). The AGP `namespace` above
        // intentionally stays dev.patrickgold.florisboard: it only scopes
        // the R class and source packages, and keeping it preserves
        // upstream cherry-pick ergonomics. Installed identity, provider
        // authorities (${applicationId} placeholders), and the
        // AppPackageContract action/permission namespaces all derive from
        // this value.
        applicationId = "io.github.sysadmindoc.swiftfloris"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        // What's-new feature contract F14 — compile-time excerpt sourced
        // from the tracked fastlane changelog for this versionCode (local-only
        // CHANGELOG.md as fallback) so Settings → About can show it offline
        // (no INTERNET, no runtime file IO). Empty when no source matches.
        buildConfigField(
            "String",
            "WHATS_NEW",
            "\"${whatsNewExcerpt(projectVersionName.substringBefore("-")).escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "RELEASE_HISTORY",
            "\"${releaseHistoryExcerpt().escapeForBuildConfig()}\"",
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
            it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            it.useJUnitPlatform()
        }
    }
}

aboutLibraries {
    collect {
        configPath = file("src/main/config")
    }
}

roborazzi {
    outputDir.set(file("src/test/snapshots/preview_filtered"))
    @OptIn(ExperimentalRoborazziApi::class)
    generateComposePreviewRobolectricTests {
        enable.set(true)
        packages.set(listOf("dev.patrickgold.florisboard.app"))
        includePrivatePreviews.set(true)
        annotationFilter.set(AnnotationFilter.Filter.RoboPreviewInclude)
        robolectricConfig.set(
            mapOf(
                "sdk" to "[35]",
                "qualifiers" to "\"w360dp-h640dp-xxhdpi\"",
            ),
        )
        generatedTestClassCount.set(1)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

tasks.withType<Test> {
    maxHeapSize = "2g"
    forkEvery = 100L
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
    }
    useJUnitPlatform()
}

kover {
    useJacoco()
}

// ROADMAP §6 N7.1 — Pin the no-network promise in code, not just in marketing.
// Fails the build if a permission outside the enrollment allowlist appears in
// either the :app source manifests OR the variant's merged manifest (which
// folds in every library AAR and every flavor/buildType overlay). A library
// that tries to add an exfiltration-capable permission via manifest-merging is
// caught by the merged-manifest check.
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
@Suppress("UNCHECKED_CAST")
private val enrollmentPolicy = JsonSlurper()
    .parse(file("src/main/config/trust-capabilities.json")) as Map<String, Any>

@Suppress("UNCHECKED_CAST")
private val enrollmentConfig = enrollmentPolicy["enrollment"] as Map<String, Any>

@Suppress("UNCHECKED_CAST")
private val enrollmentAllowedPermissions = enrollmentConfig["allowedPermissions"] as List<String>

private val enrollmentSignaturePermissionPrefix = enrollmentConfig["signaturePermissionPrefix"] as String

@Suppress("UNCHECKED_CAST")
private val baseManifestPermissions = enrollmentConfig["baseManifestPermissions"] as List<String>

@Suppress("UNCHECKED_CAST")
private val baseManifestPermissionPrefixes = enrollmentConfig["baseManifestPermissionPrefixes"] as List<String>

private fun isEnrollmentPermissionAllowed(permission: String): Boolean {
    return permission in enrollmentAllowedPermissions ||
        permission.startsWith(enrollmentSignaturePermissionPrefix) ||
        permission in baseManifestPermissions ||
        baseManifestPermissionPrefixes.any(permission::startsWith)
}

// Match a <uses-permission ...> element that declares a permission outside the
// explicit enrollment allowlist AND does NOT carry tools:node="remove" /
// "removeAll". Multi-line tolerant.
fun findDisallowedPermissionViolations(manifestText: String): List<String> {
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
        if (!isEnrollmentPermissionAllowed(name)) {
            violations += name
        }
    }
    return violations
}

val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Fails the build if any source AndroidManifest.xml declares a permission outside the enrollment allowlist (ROADMAP §6 N7.1)."

    val manifests = fileTree("src") {
        include("**/AndroidManifest.xml")
    }
    inputs.files(manifests).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { true }

    doLast {
        val violations = mutableListOf<String>()
        manifests.forEach { manifest ->
            val text = manifest.readText()
            findDisallowedPermissionViolations(text).forEach { perm ->
                violations += "${manifest.relativeTo(projectDir)} declares $perm"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("SwiftFloris enrollment permission contract violation (ROADMAP §6 N7.1):")
                    violations.forEach { appendLine("  - $it") }
                    appendLine()
                    append("Every permission must be explicitly allowlisted or belong to SwiftFloris's signature namespace. ")
                    append("If a feature genuinely needs a new permission, update the policy and its threat-model review together.")
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
            description = "Fails the build if the merged AndroidManifest for variant ${variant.name} declares a permission outside the enrollment allowlist (ROADMAP §6 N7.1)."

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
                val violations = findDisallowedPermissionViolations(text)
                if (violations.isNotEmpty()) {
                    throw GradleException(
                        buildString {
                            appendLine("SwiftFloris enrollment permission contract violation in MERGED manifest for $variantName (ROADMAP §6 N7.1):")
                            violations.distinct().forEach { appendLine("  - $it") }
                            appendLine()
                            append("A library / AAR introduced a permission outside the enrollment allowlist via manifest merging. ")
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

// The load-bearing excludes in `app/src/main/res/xml/data_extraction_rules.xml`
// have a single owner: `BackupDataInventoryTest`. It matches both the
// <cloud-backup> and <device-transfer> exclude sets exactly against
// `BackupDataInventory.requiredAndroidExcludes()`, so adding a persisted store
// without an exclude fails there and names the file.
//
// A `verifyDataExtractionRules` Gradle task used to pin a hand-written subset
// here as well. It covered 13 of the 22 paths, omitting the Tasker HMAC secret,
// the clipboard history and its keys, and the scheduled-backup prefs, so
// deleting any of those left it green. Two owners for one list means the weaker
// one silently certifies what it does not check; the stronger one is kept.

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
    implementation(libs.androidx.work.runtime)
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
    compileOnly(libs.roborazzi.annotations)

    implementation(project(":lib:android"))
    implementation(project(":lib:color"))
    implementation(project(":lib:compose"))
    implementation(project(":lib:kotlin"))
    //implementation(project(":lib:native"))  // Rust native - optional for keyboard core
    implementation(project(":lib:snygg"))

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // ROADMAP §7 Next-12.2 — Roborazzi Compose screenshot regression suite.
    // Robolectric-backed so the snapshots run on the JVM (no device, no
    // emulator), which makes them cheap to run during local release checks.
    // junit-vintage-engine lets the JUnit-4-style Robolectric tests run
    // under the project-wide `useJUnitPlatform()` runner alongside the
    // Kotest 5 suites.
    testImplementation(libs.composable.preview.scanner)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.annotations)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
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

// What's-new feature contract F14 — compile-time release notes for the offline
// "What's new" surface. Primary source is the tracked per-versionCode fastlane
// changelog (CHANGELOG.md is local-only since 886c4aa and absent from CI
// checkouts, so it can only ever be a local-build fallback). Returns "" when
// no source matches (e.g. a dev build between releases), in which case the
// Settings entry hides itself.
fun whatsNewExcerpt(versionName: String, maxChars: Int = 900): String {
    val fastlaneChangelog = rootProject.file(
        "fastlane/metadata/android/en-US/changelogs/$projectVersionCode.txt"
    )
    if (fastlaneChangelog.exists()) {
        val body = fastlaneChangelog.readText().trim()
        if (body.isNotEmpty()) {
            return if (body.length > maxChars) {
                body.substring(0, maxChars).substringBeforeLast('\n').trimEnd() + "\n…"
            } else {
                body
            }
        }
    }
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

fun releaseHistoryExcerpt(maxEntries: Int = 12, maxCharsPerEntry: Int = 700): String {
    val changelogDir = rootProject.file("fastlane/metadata/android/en-US/changelogs")
    if (!changelogDir.exists()) return ""
    return changelogDir.listFiles { file ->
        file.isFile && file.extension == "txt" && file.nameWithoutExtension.toIntOrNull() != null
    }
        .orEmpty()
        .sortedByDescending { it.nameWithoutExtension.toInt() }
        .take(maxEntries)
        .mapNotNull { file ->
            val body = file.readText().trim()
            if (body.isEmpty()) {
                null
            } else {
                val clipped = if (body.length > maxCharsPerEntry) {
                    body.substring(0, maxCharsPerEntry).substringBeforeLast('\n').trimEnd() + "\n..."
                } else {
                    body
                }
                "Build ${file.nameWithoutExtension}\n$clipped"
            }
        }
        .joinToString("\n\n")
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
