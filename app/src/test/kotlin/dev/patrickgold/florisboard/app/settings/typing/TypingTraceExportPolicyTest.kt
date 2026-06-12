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

package dev.patrickgold.florisboard.app.settings.typing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TypingTraceExportPolicyTest : FunSpec({
    test("raw trace sharing is the only sensitive external action") {
        val raw = TypingTraceExportPolicy.stateFor(TypingTraceAction.ShareRawTrace, traceFileBytes = 128L)
        val fixtures = TypingTraceExportPolicy.stateFor(TypingTraceAction.ShareReplayFixtures, traceFileBytes = 128L)
        val clear = TypingTraceExportPolicy.stateFor(TypingTraceAction.ClearTrace, traceFileBytes = 128L)

        raw.enabled shouldBe true
        raw.sharesExternally shouldBe true
        raw.containsSensitiveRawTraceFields shouldBe true
        raw.requiresSensitiveContentConfirmation shouldBe true
        raw.isRecommendedDebugExport shouldBe false

        fixtures.enabled shouldBe true
        fixtures.sharesExternally shouldBe true
        fixtures.containsSensitiveRawTraceFields shouldBe false
        fixtures.requiresSensitiveContentConfirmation shouldBe false
        fixtures.isRecommendedDebugExport shouldBe true

        clear.enabled shouldBe true
        clear.sharesExternally shouldBe false
        clear.containsSensitiveRawTraceFields shouldBe false
        clear.requiresSensitiveContentConfirmation shouldBe false
        clear.isRecommendedDebugExport shouldBe false
    }

    test("all trace actions are disabled when no trace exists") {
        TypingTraceAction.entries.forEach { action ->
            TypingTraceExportPolicy.stateFor(action, traceFileBytes = 0L).enabled shouldBe false
        }
    }

    test("replay fixtures are the recommended debugging export") {
        TypingTraceExportPolicy.recommendedDebugExportAction shouldBe TypingTraceAction.ShareReplayFixtures
    }
})
