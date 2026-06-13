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

package dev.patrickgold.florisboard.lib

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FlorisLocaleTest : FunSpec({

    data class CapabilityCase(
        val tag: String,
        val supportsCapitalization: Boolean,
        val supportsAutoSpace: Boolean,
    )

    test("language capability gates pin script-sensitive locales") {
        val cases = listOf(
            CapabilityCase("ja", supportsCapitalization = false, supportsAutoSpace = false),
            CapabilityCase("ja-JP", supportsCapitalization = false, supportsAutoSpace = false),
            CapabilityCase("zh", supportsCapitalization = false, supportsAutoSpace = false),
            CapabilityCase("ko", supportsCapitalization = false, supportsAutoSpace = false),
            CapabilityCase("th", supportsCapitalization = false, supportsAutoSpace = false),
            CapabilityCase("bn", supportsCapitalization = false, supportsAutoSpace = true),
            CapabilityCase("hi", supportsCapitalization = false, supportsAutoSpace = true),
            CapabilityCase("en", supportsCapitalization = true, supportsAutoSpace = true),
            CapabilityCase("de", supportsCapitalization = true, supportsAutoSpace = true),
            CapabilityCase("ru", supportsCapitalization = true, supportsAutoSpace = true),
            CapabilityCase("el", supportsCapitalization = true, supportsAutoSpace = true),
            CapabilityCase("ar", supportsCapitalization = false, supportsAutoSpace = true),
            CapabilityCase("he", supportsCapitalization = false, supportsAutoSpace = true),
            CapabilityCase("ta", supportsCapitalization = false, supportsAutoSpace = true),
            CapabilityCase("fr", supportsCapitalization = true, supportsAutoSpace = true),
            CapabilityCase("uk", supportsCapitalization = true, supportsAutoSpace = true),
        )

        for (case in cases) {
            val locale = FlorisLocale.fromTag(case.tag)

            locale.supportsCapitalization shouldBe case.supportsCapitalization
            locale.supportsAutoSpace shouldBe case.supportsAutoSpace
        }
    }

    test("language and locale tags keep their delimiter contracts") {
        val locale = FlorisLocale.from("ja", "JP", "POSIX")

        locale.languageTag() shouldBe "ja-JP-POSIX"
        locale.localeTag() shouldBe "ja_JP_POSIX"
        FlorisLocale.fromTag(locale.languageTag()) shouldBe locale
        FlorisLocale.fromTag(locale.localeTag()) shouldBe locale
    }
})
