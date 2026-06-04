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

package dev.patrickgold.florisboard.ime.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PerAppAccentDiscoveryHintTrackerTest : FunSpec({
    test("three distinct editor packages make the discovery hint ready") {
        val tracker = PerAppAccentDiscoveryHintTracker(appPackageName = "dev.patrickgold.florisboard")
        var state = PerAppAccentDiscoveryHintState.COLLECTING

        state = tracker.observe("com.slack", state, perAppAccentEnabled = false)
        state shouldBe PerAppAccentDiscoveryHintState.COLLECTING

        state = tracker.observe("com.whatsapp", state, perAppAccentEnabled = false)
        state shouldBe PerAppAccentDiscoveryHintState.COLLECTING

        state = tracker.observe("org.telegram.messenger", state, perAppAccentEnabled = false)
        state shouldBe PerAppAccentDiscoveryHintState.READY
    }

    test("blank duplicate and self package names do not advance the threshold") {
        val tracker = PerAppAccentDiscoveryHintTracker(appPackageName = "dev.patrickgold.florisboard")
        var state = PerAppAccentDiscoveryHintState.COLLECTING

        listOf(
            "",
            "   ",
            "dev.patrickgold.florisboard",
            "com.slack",
            "com.slack",
            "com.whatsapp",
        ).forEach { packageName ->
            state = tracker.observe(packageName, state, perAppAccentEnabled = false)
        }

        state shouldBe PerAppAccentDiscoveryHintState.COLLECTING
    }

    test("enabled feature and terminal states suppress future collection") {
        val enabledTracker = PerAppAccentDiscoveryHintTracker(appPackageName = "dev.patrickgold.florisboard")
        enabledTracker.observe(
            packageName = "com.slack",
            state = PerAppAccentDiscoveryHintState.COLLECTING,
            perAppAccentEnabled = true,
        ) shouldBe PerAppAccentDiscoveryHintState.DISMISSED

        val readyTracker = PerAppAccentDiscoveryHintTracker(appPackageName = "dev.patrickgold.florisboard")
        readyTracker.observe(
            packageName = "com.slack",
            state = PerAppAccentDiscoveryHintState.READY,
            perAppAccentEnabled = false,
        ) shouldBe PerAppAccentDiscoveryHintState.READY

        val dismissedTracker = PerAppAccentDiscoveryHintTracker(appPackageName = "dev.patrickgold.florisboard")
        dismissedTracker.observe(
            packageName = "com.slack",
            state = PerAppAccentDiscoveryHintState.DISMISSED,
            perAppAccentEnabled = false,
        ) shouldBe PerAppAccentDiscoveryHintState.DISMISSED
    }
})
