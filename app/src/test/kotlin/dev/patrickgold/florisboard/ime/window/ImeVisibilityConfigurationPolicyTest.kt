/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.window

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImeVisibilityConfigurationPolicyTest : FunSpec({
    test("restores only when Android 17 target app had the IME window shown") {
        ImeVisibilityConfigurationPolicy.shouldRestoreImeAfterConfigurationChange(
            wasWindowShown = true,
            runtimeSdk = 37,
            targetSdk = 37,
        ) shouldBe true
    }

    test("keeps current targetSdk 36 behavior unchanged") {
        ImeVisibilityConfigurationPolicy.shouldRestoreImeAfterConfigurationChange(
            wasWindowShown = true,
            runtimeSdk = 37,
            targetSdk = 36,
        ) shouldBe false
    }

    test("does not restore on pre-Android 17 runtimes") {
        ImeVisibilityConfigurationPolicy.shouldRestoreImeAfterConfigurationChange(
            wasWindowShown = true,
            runtimeSdk = 36,
            targetSdk = 37,
        ) shouldBe false
    }

    test("does not reopen an IME window that was hidden before configuration change") {
        ImeVisibilityConfigurationPolicy.shouldRestoreImeAfterConfigurationChange(
            wasWindowShown = false,
            runtimeSdk = 37,
            targetSdk = 37,
        ) shouldBe false
    }
})
