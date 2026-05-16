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

package dev.patrickgold.florisboard.ime.smartcompose

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AddonConsentStateTest : FunSpec({

    test("only GRANTED allows invocation through the router") {
        AddonConsentState.NEEDS_PROMPT.allowsInvocation() shouldBe false
        AddonConsentState.DENIED.allowsInvocation() shouldBe false
        AddonConsentState.GRANTED.allowsInvocation() shouldBe true
    }

    test("default state for unset prefs is NEEDS_PROMPT (one-time dialog on first use)") {
        // Pin the enum default value contract — the routers depend on this for first-use suppression
        // until the consent dialog flips the pref to GRANTED.
        AddonConsentState.values().first() shouldBe AddonConsentState.NEEDS_PROMPT
    }

    test("all three states are exhaustively defined") {
        AddonConsentState.values().toSet() shouldBe setOf(
            AddonConsentState.NEEDS_PROMPT,
            AddonConsentState.GRANTED,
            AddonConsentState.DENIED,
        )
    }
})
