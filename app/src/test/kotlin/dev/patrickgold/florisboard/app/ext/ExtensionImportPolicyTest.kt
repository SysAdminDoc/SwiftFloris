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

package dev.patrickgold.florisboard.app.ext

import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.lib.NATIVE_NULLPTR
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionComponent
import dev.patrickgold.florisboard.lib.ext.ExtensionEditor
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExtensionImportPolicyTest : FunSpec({
    test("language pack import accepts a new valid pack") {
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = true,
            extension = languagePack(),
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.None,
        ) shouldBe ExtensionImportDecision(
            skipReason = NATIVE_NULLPTR.toInt(),
            action = ExtensionImportAction.NewInstall,
        )
    }

    test("language pack import classifies a user-installed existing pack as an update") {
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = true,
            extension = languagePack(version = "2.0"),
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.UserInstalled,
        ) shouldBe ExtensionImportDecision(
            skipReason = NATIVE_NULLPTR.toInt(),
            action = ExtensionImportAction.Update,
        )
    }

    test("language pack import blocks updates over bundled core packs") {
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = true,
            extension = languagePack(id = "org.florisboard.languagepack", version = "2.0"),
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.BundledAsset,
        ).skipReason shouldBe R.string.ext__import__file_skip_ext_core
    }

    test("language pack import rejects corrupted metadata") {
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = true,
            extension = languagePack(id = "../escape"),
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.None,
        ).skipReason shouldBe R.string.ext__import__file_skip_ext_corrupted
    }

    test("language pack import rejects non language pack extensions") {
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = true,
            extension = keyboardExtension(),
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.None,
        ).skipReason shouldBe R.string.ext__import__file_skip_unsupported
    }

    test("language pack import rejects unsupported files and missing parsed extensions") {
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = false,
            extension = languagePack(),
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.None,
        ).skipReason shouldBe R.string.ext__import__file_skip_unsupported
        ExtensionImportPolicy.decideFile(
            fileMatchesFilter = true,
            extension = null,
            requestedType = ExtensionImportScreenType.EXT_LANGUAGEPACK,
            existingSource = ExtensionImportExistingSource.None,
        ).skipReason shouldBe R.string.ext__import__file_skip_ext_corrupted
    }

    test("import button eligibility follows importable decisions") {
        ExtensionImportPolicy.canImport(
            listOf(
                ExtensionImportDecision(
                    skipReason = R.string.ext__import__file_skip_unsupported,
                    action = null,
                ),
                ExtensionImportDecision(
                    skipReason = R.string.ext__import__file_skip_ext_corrupted,
                    action = null,
                ),
            ),
        ) shouldBe false
        ExtensionImportPolicy.canImport(
            listOf(
                ExtensionImportDecision(
                    skipReason = R.string.ext__import__file_skip_unsupported,
                    action = null,
                ),
                ExtensionImportDecision(
                    skipReason = NATIVE_NULLPTR.toInt(),
                    action = ExtensionImportAction.Update,
                ),
            ),
        ) shouldBe true
    }
})

private fun languagePack(
    id: String = "org.example.languagepack",
    version: String = "1.0",
): Extension {
    return TestExtension(
        meta = extensionMeta(id = id, version = version, title = "Example language pack"),
        serialType = LanguagePackExtension.SERIAL_TYPE,
    )
}

private fun keyboardExtension(): Extension {
    return TestExtension(
        meta = extensionMeta(id = "org.example.keyboard", title = "Example keyboard"),
        serialType = KeyboardExtension.SERIAL_TYPE,
    )
}

private fun extensionMeta(
    id: String,
    version: String = "1.0",
    title: String,
): ExtensionMeta {
    return ExtensionMeta(
        id = id,
        version = version,
        title = title,
        maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
        license = "Apache-2.0",
    )
}

private class TestExtension(
    override val meta: ExtensionMeta,
    private val serialType: String,
) : Extension() {
    override val dependencies: List<String>? = null

    override fun serialType(): String = serialType

    override fun components(): List<ExtensionComponent> = emptyList()

    override fun edit(): ExtensionEditor = error("Test extension is immutable")
}
