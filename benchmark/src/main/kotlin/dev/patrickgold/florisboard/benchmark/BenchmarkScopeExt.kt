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

import androidx.benchmark.macro.MacrobenchmarkScope

internal fun MacrobenchmarkScope.selectTargetIme() {
    var enableResult = ""
    var setResult = ""
    var selected = ""
    repeat(5) { attempt ->
        enableResult = device.executeShellCommand("ime enable $TargetImeComponent").trim()
        setResult = device.executeShellCommand("ime set $TargetImeComponent").trim()
        selected = device.executeShellCommand("settings get secure default_input_method").trim()
        if (selected == TargetImeComponent) {
            return
        }
        Thread.sleep((attempt + 1) * 250L)
    }
    error(
        "Unable to select $TargetImeComponent; default=$selected, " +
            "enable='$enableResult', set='$setResult'",
    )
}

internal fun MacrobenchmarkScope.startBenchmarkInputActivityAndWait() {
    startComponentAndWait(TargetBenchmarkInputComponent)
}

internal fun MacrobenchmarkScope.startSettingsActivityAndWait() {
    startComponentAndWait(TargetSettingsComponent)
}

private fun MacrobenchmarkScope.startComponentAndWait(component: String) {
    val result = device.executeShellCommand("am start -W -n $component").trim()
    if (!result.contains("Status: ok")) {
        error("Unable to start $component: $result")
    }
    device.waitForIdle(5_000)
}
