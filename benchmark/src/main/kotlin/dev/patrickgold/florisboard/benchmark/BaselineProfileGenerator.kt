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

package dev.patrickgold.florisboard.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() =
        baselineProfileRule.collect(packageName = TargetPackageName) {
            pressHome()
            startSettingsActivityAndWait()
            device.waitForIdle()
        }

    @Test
    fun keyboardTypingJourney() =
        baselineProfileRule.collect(packageName = TargetPackageName) {
            pressHome()
            selectTargetIme()
            startBenchmarkInputActivityAndWait()
            device.waitForIdle(2_000)
            device.executeShellCommand("input text 'hello world this is a test'")
            device.waitForIdle(2_000)
            device.executeShellCommand("input text ' the quick brown fox'")
            device.waitForIdle(1_000)
        }
}
