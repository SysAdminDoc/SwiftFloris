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

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.ActivityNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * `Settings.ACTION_HARD_KEYBOARD_SETTINGS` does not resolve on every build. The click handler used
 * to let that exception escape; these cases pin the guarded outcome instead.
 */
class SystemSettingsLaunchPolicyTest : FunSpec({

    test("a successful launch reports success and leaves the current notice alone") {
        var launched = false

        val result = SystemSettingsLaunchPolicy.launchGuarded { launched = true }

        launched shouldBe true
        result shouldBe true
        SystemSettingsLaunchPolicy.noticeFor(result, PhysicalKeyboardNotice.ImportSuccess) shouldBe
            PhysicalKeyboardNotice.ImportSuccess
    }

    test("a missing settings activity is reported, not thrown") {
        val result = SystemSettingsLaunchPolicy.launchGuarded {
            throw ActivityNotFoundException("no hard keyboard settings")
        }

        result shouldBe false
        SystemSettingsLaunchPolicy.noticeFor(result, PhysicalKeyboardNotice.None) shouldBe
            PhysicalKeyboardNotice.SystemSettingsUnavailable
    }

    test("a restricted settings activity is reported the same way") {
        val result = SystemSettingsLaunchPolicy.launchGuarded {
            throw SecurityException("blocked for this user")
        }

        result shouldBe false
        SystemSettingsLaunchPolicy.noticeFor(result, PhysicalKeyboardNotice.ApplySuccess) shouldBe
            PhysicalKeyboardNotice.SystemSettingsUnavailable
    }

    test("unrelated failures still surface instead of being swallowed") {
        shouldThrow<IllegalStateException> {
            SystemSettingsLaunchPolicy.launchGuarded { error("programming error") }
        }
    }
})
