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

package dev.patrickgold.florisboard.app.settings.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SettingsSearchScreenStateTest : FunSpec({
    test("result scroll resets only for populated non-blank queries") {
        shouldResetSearchResultsScroll("theme", resultCount = 3) shouldBe true

        shouldResetSearchResultsScroll("", resultCount = 3) shouldBe false
        shouldResetSearchResultsScroll("   ", resultCount = 3) shouldBe false
        shouldResetSearchResultsScroll("missing", resultCount = 0) shouldBe false
    }
})
