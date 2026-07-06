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

package org.florisboard.lib.kotlin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LibraryTest : FunSpec({
    test("tryOrNull returns the block result") {
        tryOrNull { "ready" } shouldBe "ready"
    }

    test("tryOrNull preserves successful null results") {
        tryOrNull<String?> { null } shouldBe null
    }

    test("tryOrNull converts thrown work to null") {
        tryOrNull {
            error("boom")
        } shouldBe null
    }
})
