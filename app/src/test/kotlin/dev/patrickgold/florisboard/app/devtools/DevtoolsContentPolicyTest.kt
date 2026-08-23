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

package dev.patrickgold.florisboard.app.devtools

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DevtoolsContentPolicyTest : FunSpec({
    test("ordinary debug sessions may expose raw diagnostic content") {
        DevtoolsContentPolicy.canExposeRawContent(
            isPasswordOrSensitiveField = false,
            isIncognitoMode = false,
            isNoPersonalizedLearningField = false,
        ) shouldBe true
    }

    test("password sessions suppress raw diagnostic content") {
        DevtoolsContentPolicy.canExposeRawContent(
            isPasswordOrSensitiveField = true,
            isIncognitoMode = false,
            isNoPersonalizedLearningField = false,
        ) shouldBe false
    }

    test("incognito sessions suppress raw diagnostic content") {
        DevtoolsContentPolicy.canExposeRawContent(
            isPasswordOrSensitiveField = false,
            isIncognitoMode = true,
            isNoPersonalizedLearningField = false,
        ) shouldBe false
    }

    test("no-personalized-learning sessions suppress raw diagnostic content") {
        DevtoolsContentPolicy.canExposeRawContent(
            isPasswordOrSensitiveField = false,
            isIncognitoMode = false,
            isNoPersonalizedLearningField = true,
        ) shouldBe false
    }

    test("every combination containing a privacy signal stays suppressed") {
        for (mask in 1..7) {
            DevtoolsContentPolicy.canExposeRawContent(
                isPasswordOrSensitiveField = mask and 1 != 0,
                isIncognitoMode = mask and 2 != 0,
                isNoPersonalizedLearningField = mask and 4 != 0,
            ) shouldBe false
        }
    }
})
