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

package dev.patrickgold.florisboard.ime.voice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VoiceInputSetupIntentContractTest : FunSpec({
    test("accepts a single valid setup reason extra") {
        VoiceInputSetupIntentContract.reasonFromExtras(
            keys = setOf("reason"),
            reasonName = VoiceInputSetupReason.FUTO_NOT_ENABLED.name,
        ) shouldBe VoiceInputSetupReason.FUTO_NOT_ENABLED
    }

    test("rejects missing reason extra") {
        VoiceInputSetupIntentContract.reasonFromExtras(
            keys = emptySet(),
            reasonName = null,
        ) shouldBe null
    }

    test("rejects unknown setup reason values") {
        VoiceInputSetupIntentContract.reasonFromExtras(
            keys = setOf("reason"),
            reasonName = "OPEN_ARBITRARY_SETTINGS",
        ) shouldBe null
    }

    test("rejects unexpected extras") {
        VoiceInputSetupIntentContract.reasonFromExtras(
            keys = setOf("reason", "packageName"),
            reasonName = VoiceInputSetupReason.NO_ENABLED_PROVIDER.name,
        ) shouldBe null
    }
})
