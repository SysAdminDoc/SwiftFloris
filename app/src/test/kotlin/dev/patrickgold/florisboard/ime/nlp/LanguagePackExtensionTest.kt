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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LanguagePackExtensionTest : FunSpec({
    test("default language packs remain Han shape-based for legacy extension compatibility") {
        val component = languagePackComponent("zh-Hans")
        val extension = languagePackExtension(items = listOf(component))

        extension.supportsHanShapeBased() shouldBe true
        extension.hanShapeBasedComponents() shouldBe listOf(component)
    }

    test("generic language packs do not participate in Han SQLite loading") {
        val extension = languagePackExtension(
            kind = LanguagePackKind.GENERIC,
            items = listOf(languagePackComponent("en")),
        )

        extension.supportsHanShapeBased() shouldBe false
        extension.hanShapeBasedComponents().shouldBeEmpty()
    }

    test("editor preserves explicit language pack kind") {
        val extension = languagePackExtension(kind = LanguagePackKind.GENERIC)
        val rebuilt = extension.edit().build()

        rebuilt.kind shouldBe LanguagePackKind.GENERIC
    }
})

private fun languagePackExtension(
    kind: LanguagePackKind = LanguagePackKind.HAN_SHAPE_BASED,
    items: List<LanguagePackComponent> = emptyList(),
): LanguagePackExtension {
    return LanguagePackExtension(
        meta = ExtensionMeta(
            id = "org.example.languagepack",
            version = "1.0",
            title = "Example language pack",
            maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
            license = "Apache-2.0",
        ),
        kind = kind,
        items = items,
    )
}

private fun languagePackComponent(localeTag: String): LanguagePackComponent {
    return LanguagePackComponent(
        id = localeTag,
        label = localeTag,
        authors = listOf("SwiftFloris"),
    )
}
