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

package dev.patrickgold.florisboard.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The Settings language picker used to carry a hand-written list of language tags. It drifted:
 * `values-ast-rES`, `values-et-rEE`, `values-sq-rAL` and `values-ur-rPK` all shipped translated
 * strings that no picker entry could select. The list is now generated at build time from the
 * resource directories, and this test is what keeps the two from separating again.
 */
class AppUiLocaleTest : FunSpec({
    fun resourceLocaleTags(): List<String> {
        val res = sequenceOf(File("app/src/main/res"), File("src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("resource directory is not reachable from ${File(".").absolutePath}")
        val localeDirectory = Regex("^values-([a-z]{2,3})(?:-r([A-Z]{2}))?$")
        return res.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { directory ->
                localeDirectory.matchEntire(directory.name)?.let { match ->
                    val (language, region) = match.destructured
                    if (region.isEmpty()) language else "$language-$region"
                }
            }
            .sorted()
    }

    test("every shipped translation is selectable in the language picker") {
        val onDisk = resourceLocaleTags()

        onDisk.size shouldBeGreaterThan 40
        AppUiLocale.shippedTags shouldBe onDisk
    }

    test("the four translations the hand-written list had lost are selectable") {
        // Regression guard for the specific drift, so a future refactor that reintroduces a
        // literal list fails with a message that names the symptom rather than a size mismatch.
        AppUiLocale.shippedTags shouldContainAll listOf("ast-ES", "et-EE", "sq-AL", "ur-PK")
    }

    test("values-night is a UI mode, not a locale, and never reaches the picker") {
        AppUiLocale.shippedTags shouldNotContain "night"
    }

    test("the follow-the-system entry is not itself a shipped locale") {
        AppUiLocale.shippedTags shouldNotContain AppUiLocale.SYSTEM_DEFAULT_TAG
    }

    test("parsing tolerates the generated field's shape without inventing entries") {
        AppUiLocale.parseShippedTags("") shouldBe emptyList()
        AppUiLocale.parseShippedTags("de, fr ,de") shouldBe listOf("de", "fr")
        AppUiLocale.parseShippedTags("auto,de") shouldBe listOf("de")
    }
})
