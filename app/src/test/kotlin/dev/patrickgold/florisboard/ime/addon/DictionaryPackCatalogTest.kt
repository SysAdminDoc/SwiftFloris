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

package dev.patrickgold.florisboard.ime.addon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val CATALOG_SHA =
    "CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:" +
        "CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC:CC"

private fun catalogManifest(
    packageName: String,
    type: AddonType = AddonType.DICTIONARY_PACK,
    displayName: String = packageName,
): AddonManifest = AddonManifest(
    packageName = packageName,
    type = type,
    version = 1L,
    displayName = displayName,
    descriptorResourceId = 1234,
    licenseSpdxId = "Apache-2.0",
    signingCertSha256 = CATALOG_SHA,
    bundleSizeBytes = 2048L,
)

private fun descriptorJson(
    language: String,
    displayName: String,
    schema: Int = 1,
    minSchemaCompat: Int = 1,
): String = """
    {
      "schema": $schema,
      "language": "$language",
      "displayName": "$displayName",
      "wordCount": 250000,
      "fldicAssetPath": "ime/dict/$language.fldic",
      "zipfAssetPath": "freq/$language.tsv",
      "source": "OpenSubtitles 2024 + Wiktionary",
      "license": "CC-BY-SA-4.0",
      "minSchemaCompat": $minSchemaCompat
    }
""".trimIndent()

class DictionaryPackCatalogTest : FunSpec({
    test("build accepts compatible dictionary-pack descriptors and sorts by language") {
        val polish = catalogManifest("org.swiftfloris.dict.pl", displayName = "Polish APK")
        val german = catalogManifest("org.swiftfloris.dict.de", displayName = "German APK")
        val theme = catalogManifest(
            packageName = "org.swiftfloris.theme.dark",
            type = AddonType.THEME_PACK,
            displayName = "Dark Theme",
        )

        val catalog = DictionaryPackCatalog.build(
            manifests = listOf(polish, theme, german),
            descriptorJsonByPackageName = mapOf(
                "org.swiftfloris.dict.pl" to descriptorJson("pl", "Polish"),
                "org.swiftfloris.dict.de" to descriptorJson("de", "Deutsch"),
            ),
        )

        catalog.entries.map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.de",
            "org.swiftfloris.dict.pl",
        )
        catalog.forLanguage("PL").map { it.packageName } shouldContainExactly listOf(
            "org.swiftfloris.dict.pl",
        )
        catalog.rejected shouldBe emptyList()
        catalog.entries.first { it.language == "pl" }.provenanceReport.toPlainText() shouldContain
            "Dataset license:   CC-BY-SA-4.0"
    }

    test("build rejects dictionary packs without descriptor JSON") {
        val manifest = catalogManifest("org.swiftfloris.dict.pl", displayName = "Polish")

        val catalog = DictionaryPackCatalog.build(
            manifests = listOf(manifest),
            descriptorJsonByPackageName = emptyMap(),
        )

        catalog.entries shouldBe emptyList()
        catalog.rejected.single().packageName shouldBe "org.swiftfloris.dict.pl"
        catalog.rejected.single().reason shouldBe "missing descriptor JSON"
    }

    test("build rejects malformed descriptor JSON") {
        val manifest = catalogManifest("org.swiftfloris.dict.pl", displayName = "Polish")

        val catalog = DictionaryPackCatalog.build(
            manifests = listOf(manifest),
            descriptorJsonByPackageName = mapOf("org.swiftfloris.dict.pl" to "not json"),
        )

        catalog.entries shouldBe emptyList()
        catalog.rejected.single().reason shouldBe "invalid descriptor JSON"
    }

    test("build rejects future incompatible descriptor schemas") {
        val manifest = catalogManifest("org.swiftfloris.dict.pl", displayName = "Polish")

        val catalog = DictionaryPackCatalog.build(
            manifests = listOf(manifest),
            descriptorJsonByPackageName = mapOf(
                "org.swiftfloris.dict.pl" to descriptorJson(
                    language = "pl",
                    displayName = "Polish",
                    schema = 99,
                    minSchemaCompat = 99,
                ),
            ),
        )

        catalog.entries shouldBe emptyList()
        catalog.rejected.single().reason shouldBe
            "descriptor schema 99 is incompatible with IME schema 1"
    }
})
