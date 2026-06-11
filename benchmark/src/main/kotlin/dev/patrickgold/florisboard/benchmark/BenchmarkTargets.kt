/*
 * Copyright (C) 2026 SwiftFloris Contributors
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

package dev.patrickgold.florisboard.benchmark

// Installed package = applicationId + .bench variant suffix. The component
// CLASS names below intentionally keep the dev.patrickgold.florisboard
// source-package prefix — Kotlin packages did not move in the app-ID
// migration, only the installed identity did.
internal const val TargetPackageName = "io.github.sysadmindoc.swiftfloris.bench"
internal const val TargetBenchmarkInputActivity =
    "dev.patrickgold.florisboard.benchmark.BenchmarkInputActivity"
internal const val TargetBenchmarkInputComponent =
    "$TargetPackageName/$TargetBenchmarkInputActivity"
internal const val TargetSettingsAlias =
    "dev.patrickgold.florisboard.SettingsLauncherAlias"
internal const val TargetSettingsComponent =
    "$TargetPackageName/$TargetSettingsAlias"
internal const val TargetImeComponent =
    "$TargetPackageName/dev.patrickgold.florisboard.FlorisImeService"
