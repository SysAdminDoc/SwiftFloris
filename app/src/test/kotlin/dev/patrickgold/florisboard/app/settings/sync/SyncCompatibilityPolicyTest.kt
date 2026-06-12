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

package dev.patrickgold.florisboard.app.settings.sync

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SyncCompatibilityPolicyTest : FunSpec({
    test("Android 8 through 12 use passphrase dictionary migration") {
        listOf(26, 27, 28, 29, 30, 31, 32).forEach { sdk ->
            val state = SyncCompatibilityPolicy.stateForSdk(sdk)

            state.route shouldBe SyncCompatibilityRoute.PassphraseDictionaryMigration
            state.supportsSealedDeviceSync shouldBe false
            state.usesPassphraseDictionaryMigration shouldBe true
        }
    }

    test("Android 13 and newer keep sealed device sync enabled") {
        listOf(33, 34, 35, 36, 37).forEach { sdk ->
            val state = SyncCompatibilityPolicy.stateForSdk(sdk)

            state.route shouldBe SyncCompatibilityRoute.SealedDeviceSync
            state.supportsSealedDeviceSync shouldBe true
            state.usesPassphraseDictionaryMigration shouldBe false
        }
    }
})
