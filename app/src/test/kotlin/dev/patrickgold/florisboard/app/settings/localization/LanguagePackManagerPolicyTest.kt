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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.nlp.LanguagePackComponent
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackKind
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LanguagePackManagerPolicyTest : FunSpec({
    test("language pack import and delete actions are disabled while deletion is busy") {
        LanguagePackManagerPolicy.canTriggerImport(isDeleteInProgress = false) shouldBe true
        LanguagePackManagerPolicy.canTriggerImport(isDeleteInProgress = true) shouldBe false

        LanguagePackManagerPolicy.canDelete(
            extensionCanBeDeleted = true,
            isDeleteInProgress = false,
        ) shouldBe true
        LanguagePackManagerPolicy.canDelete(
            extensionCanBeDeleted = false,
            isDeleteInProgress = false,
        ) shouldBe false
        LanguagePackManagerPolicy.canDelete(
            extensionCanBeDeleted = true,
            isDeleteInProgress = true,
        ) shouldBe false
    }

    test("language pack manager notice prioritizes delete progress over terminal state") {
        LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = true,
            lastTerminalNotice = LanguagePackManagerNotice.DeleteFailure,
        ) shouldBe LanguagePackManagerNotice.DeleteInProgress

        LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = false,
            lastTerminalNotice = LanguagePackManagerNotice.DeleteSuccess,
        ) shouldBe LanguagePackManagerNotice.DeleteSuccess

        LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = false,
            lastTerminalNotice = null,
        ) shouldBe LanguagePackManagerNotice.None
    }

    test("catalog marks Han packs active only when a component matches an active subtype") {
        val entries = LanguagePackManagerPolicy.catalogEntries(
            extensions = listOf(
                languagePackExtension(
                    id = "org.example.zh",
                    title = "Chinese Tables",
                    components = listOf(languagePackComponent("zh-Hans"), languagePackComponent("zh-Hant")),
                ),
            ),
            activeLocaleTags = setOf("zh_HANS", "en"),
        )

        entries.shouldHaveSize(1)
        entries.single().state shouldBe LanguagePackRuntimeState.ActiveForSubtype
        entries.single().activeComponentCount shouldBe 1
        entries.single().components.first { it.localeTag == "zh_HANS" }.isActive shouldBe true
        entries.single().components.first { it.localeTag == "zh_HANT" }.isActive shouldBe false
    }

    test("catalog keeps installed Han packs in standby when no active subtype matches") {
        val entries = LanguagePackManagerPolicy.catalogEntries(
            extensions = listOf(
                languagePackExtension(
                    id = "org.example.wubi",
                    title = "Wubi Tables",
                    components = listOf(languagePackComponent("zh-Hans")),
                ),
            ),
            activeLocaleTags = setOf("en"),
        )

        entries.single().state shouldBe LanguagePackRuntimeState.InstalledStandby
        entries.single().activeComponentCount shouldBe 0
    }

    test("catalog marks active Han packs unavailable when loaded table data failed") {
        val extension = languagePackExtension(
            id = "org.example.broken",
            title = "Broken Tables",
            components = listOf(languagePackComponent("zh-Hans")),
        )
        val entries = LanguagePackManagerPolicy.catalogEntries(
            extensions = listOf(extension),
            activeLocaleTags = setOf("zh_HANS"),
            hasUsableHanRuntime = { false },
        )

        entries.single().state shouldBe LanguagePackRuntimeState.DataUnavailable
        entries.single().activeComponentCount shouldBe 1
    }

    test("catalog shows generic language packs without making them Han startup inputs") {
        val entries = LanguagePackManagerPolicy.catalogEntries(
            extensions = listOf(
                languagePackExtension(
                    id = "org.example.generic",
                    title = "Generic Pack",
                    kind = LanguagePackKind.GENERIC,
                    components = listOf(languagePackComponent("ja")),
                ),
            ),
            activeLocaleTags = setOf("ja"),
        )

        entries.single().kind shouldBe LanguagePackKind.GENERIC
        entries.single().state shouldBe LanguagePackRuntimeState.MetadataOnly
        entries.single().activeComponentCount shouldBe 1
    }
})

private fun languagePackExtension(
    id: String,
    title: String,
    kind: LanguagePackKind = LanguagePackKind.HAN_SHAPE_BASED,
    components: List<LanguagePackComponent>,
): LanguagePackExtension {
    return LanguagePackExtension(
        meta = ExtensionMeta(
            id = id,
            version = "1.0",
            title = title,
            maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
            license = "Apache-2.0",
        ),
        kind = kind,
        items = components,
    )
}

private fun languagePackComponent(localeTag: String): LanguagePackComponent {
    return LanguagePackComponent(
        id = localeTag,
        label = localeTag,
        authors = listOf("SwiftFloris"),
    )
}
