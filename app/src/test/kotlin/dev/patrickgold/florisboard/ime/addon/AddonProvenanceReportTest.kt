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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val FAKE_SHA = "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89"

private fun fakeManifest(
    type: AddonType,
    packageName: String = "org.example.addon",
    displayName: String = "Example Addon",
    apkLicense: String = "Apache-2.0",
): AddonManifest = AddonManifest(
    packageName = packageName,
    type = type,
    version = 7L,
    displayName = displayName,
    descriptorResourceId = 1234,
    licenseSpdxId = apkLicense,
    signingCertSha256 = FAKE_SHA,
    bundleSizeBytes = 2L * 1024 * 1024,
)

class AddonProvenanceReportTest : FunSpec({
    test("from(manifest) populates APK-level fields and leaves dataset fields null") {
        val report = AddonProvenanceReport.from(fakeManifest(AddonType.THEME_PACK))

        report.packageName shouldBe "org.example.addon"
        report.addonType shouldBe AddonType.THEME_PACK
        report.apkLicenseSpdxId shouldBe "Apache-2.0"
        report.datasetLicenseSpdxId shouldBe null
        report.datasetSource shouldBe null
        report.noNetworkAttested shouldBe true
    }

    test("fromDictionaryPack lifts source + dataset license off the descriptor") {
        val manifest = fakeManifest(AddonType.DICTIONARY_PACK, apkLicense = "GPL-3.0-only")
        val descriptor = DictionaryPackDescriptor(
            schema = 1,
            language = "pl",
            displayName = "Polish (2025 baseline)",
            wordCount = 320_000,
            fldicAssetPath = "ime/dict/pl.fldic",
            zipfAssetPath = "freq/pl.tsv",
            source = "OpenSubtitles 2024 + Wiktionary",
            license = "CC-BY-SA-4.0",
        )

        val report = AddonProvenanceReport.fromDictionaryPack(manifest, descriptor)

        report.apkLicenseSpdxId shouldBe "GPL-3.0-only"
        report.datasetLicenseSpdxId shouldBe "CC-BY-SA-4.0"
        report.datasetSource shouldBe "OpenSubtitles 2024 + Wiktionary"
        report.noNetworkAttested shouldBe true
    }

    test("fromDictionaryPack refuses non-dictionary addon types") {
        val manifest = fakeManifest(AddonType.THEME_PACK)
        val descriptor = DictionaryPackDescriptor(
            schema = 1,
            language = "pl",
            displayName = "Polish",
            wordCount = 1,
            // A real path, because the descriptor now requires the format its
            // field is named for. This test is about the addon type, not the
            // path, so the placeholder was only ever incidental.
            fldicAssetPath = "ime/dict/pl.fldic",
            source = "x",
            license = "x",
        )

        try {
            AddonProvenanceReport.fromDictionaryPack(manifest, descriptor)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            e.message shouldContain "non-dictionary addon type"
        }
    }

    test("toPlainText renders dataset fields only when present and surfaces no-network attestation") {
        val manifest = fakeManifest(AddonType.DICTIONARY_PACK)
        val descriptor = DictionaryPackDescriptor(
            schema = 1,
            language = "de",
            displayName = "Deutsch",
            wordCount = 500_000,
            fldicAssetPath = "ime/dict/de.fldic",
            source = "Wortliste-Projekt",
            license = "CC-BY-SA-3.0",
        )

        val text = AddonProvenanceReport.fromDictionaryPack(manifest, descriptor).toPlainText()

        text shouldContain "Addon: Example Addon"
        text shouldContain "Package:           org.example.addon"
        text shouldContain "Type:              dictionary-pack"
        text shouldContain "Version:           7"
        text shouldContain "APK license:       Apache-2.0"
        text shouldContain "Dataset license:   CC-BY-SA-3.0"
        text shouldContain "Dataset source:    Wortliste-Projekt"
        text shouldContain "Bundle size:       2.00 MiB"
        text shouldContain "Signing fp SHA-256: $FAKE_SHA"
        text shouldContain "No-network attest: yes"
    }

    test("toPlainText omits dataset rows entirely when the descriptor was absent") {
        val text = AddonProvenanceReport.from(fakeManifest(AddonType.THEME_PACK)).toPlainText()

        text shouldContain "Type:              theme-pack"
        text shouldNotContain "Dataset license:"
        text shouldNotContain "Dataset source:"
    }

    test("toJson encodes dataset fields only when set and is round-trip stable") {
        val report = AddonProvenanceReport.from(fakeManifest(AddonType.LAYOUT_PACK))
        val parsed = Json.parseToJsonElement(report.toJson()) as JsonObject

        parsed["packageName"] shouldBe JsonPrimitive("org.example.addon")
        parsed["addonType"] shouldBe JsonPrimitive("layout-pack")
        parsed["apkLicenseSpdxId"] shouldBe JsonPrimitive("Apache-2.0")
        parsed.containsKey("datasetLicenseSpdxId") shouldBe false
        parsed.containsKey("datasetSource") shouldBe false
        parsed["noNetworkAttested"] shouldBe JsonPrimitive(true)
    }

    test("toJson includes both dataset fields for a fully-populated dictionary-pack report") {
        val report = AddonProvenanceReport.fromDictionaryPack(
            manifest = fakeManifest(AddonType.DICTIONARY_PACK),
            descriptor = DictionaryPackDescriptor(
                schema = 1,
                language = "fr",
                displayName = "French",
                wordCount = 250_000,
                fldicAssetPath = "ime/dict/fr.fldic",
                source = "Lexique 3.83",
                license = "CC-BY-SA-4.0",
            ),
        )
        val parsed = Json.parseToJsonElement(report.toJson()) as JsonObject

        parsed["datasetLicenseSpdxId"] shouldBe JsonPrimitive("CC-BY-SA-4.0")
        parsed["datasetSource"] shouldBe JsonPrimitive("Lexique 3.83")
    }

    test("sortedForDisplay orders by (addonType, packageName)") {
        val reports = listOf(
            AddonProvenanceReport.from(fakeManifest(AddonType.THEME_PACK, packageName = "z.theme.b")),
            AddonProvenanceReport.from(fakeManifest(AddonType.LANGUAGE_PACK, packageName = "x.lang.a")),
            AddonProvenanceReport.from(fakeManifest(AddonType.THEME_PACK, packageName = "a.theme.a")),
        )
        val sorted = reports.sortedForDisplay()

        sorted.map { it.packageName } shouldBe listOf("x.lang.a", "a.theme.a", "z.theme.b")
    }
})
