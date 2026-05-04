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

package dev.patrickgold.florisboard.lib.cache

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CacheManagerTest : FunSpec({
    test("sanitizeImportFileName strips path traversal and unsafe characters") {
        CacheManager.sanitizeImportFileName("../evil:name?.flex", "fallback.flex") shouldBe "evil_name_.flex"
        CacheManager.sanitizeImportFileName("..\\nested\\theme.flex", "fallback.flex") shouldBe "theme.flex"
    }

    test("sanitizeImportFileName falls back for blank or unusable names") {
        CacheManager.sanitizeImportFileName("...", "fallback.flex") shouldBe "fallback.flex"
        CacheManager.sanitizeImportFileName(null, "fallback.flex") shouldBe "fallback.flex"
    }
})
