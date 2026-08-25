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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private val POLISH_DESCRIPTOR = """
    {
      "schema": 1,
      "language": "pl",
      "displayName": "Polish (2025 baseline)",
      "wordCount": 320000,
      "fldicAssetPath": "ime/dict/pl.fldic",
      "zipfAssetPath": "freq/pl.tsv",
      "source": "OpenSubtitles 2024 + Wiktionary",
      "license": "CC-BY-SA-4.0",
      "minSchemaCompat": 1
    }
""".trimIndent()

class DictionaryPackDescriptorTest : FunSpec({
    test("parses the canonical Polish dictionary-pack descriptor") {
        val desc = DictionaryPackDescriptor.parse(POLISH_DESCRIPTOR).shouldNotBeNull()
        desc.schema shouldBe 1
        desc.language shouldBe "pl"
        desc.displayName shouldBe "Polish (2025 baseline)"
        desc.wordCount shouldBe 320_000L
        desc.fldicAssetPath shouldBe "ime/dict/pl.fldic"
        desc.zipfAssetPath shouldBe "freq/pl.tsv"
        desc.license shouldBe "CC-BY-SA-4.0"
        desc.isCompatibleWithIme() shouldBe true
    }

    test("zipfAssetPath is optional") {
        val raw = """
            {
              "schema": 1,
              "language": "de",
              "displayName": "German",
              "wordCount": 250000,
              "fldicAssetPath": "ime/dict/de.fldic",
              "source": "OpenSubtitles 2024",
              "license": "CC-BY-SA-4.0"
            }
        """.trimIndent()
        val desc = DictionaryPackDescriptor.parse(raw).shouldNotBeNull()
        desc.zipfAssetPath.shouldBeNull()
    }

    test("declines descriptors targeting a future schema version") {
        val raw = POLISH_DESCRIPTOR.replace("\"schema\": 1", "\"schema\": 99")
            .replace("\"minSchemaCompat\": 1", "\"minSchemaCompat\": 99")
        val desc = DictionaryPackDescriptor.parse(raw).shouldNotBeNull()
        desc.isCompatibleWithIme() shouldBe false
    }

    test("rejects malformed JSON without throwing") {
        DictionaryPackDescriptor.parse("not json").shouldBeNull()
        DictionaryPackDescriptor.parse("").shouldBeNull()
    }

    test("validates language must be lowercase ISO 639-1") {
        shouldThrow<IllegalArgumentException> {
            DictionaryPackDescriptor(
                schema = 1,
                language = "PL",
                displayName = "Bad",
                wordCount = 1,
                fldicAssetPath = "ime/dict/pl.fldic",
                source = "x",
                license = "MIT",
            )
        }
    }

    test("rejects a dictionary path the loader has no decoder for") {
        // decodeFrequencies routes on the suffix: exactly ".fldic" is the binary
        // format and everything else goes to the JSON parser. A path that is
        // neither reaches that parser holding bytes it cannot read, so catching
        // it at enrolment keeps a mistyped descriptor from becoming a failure on
        // the typing path.
        listOf("ime/dict/pl.txt", "ime/dict/pl", "ime/dict/fldic.gz", "ime/dict/pl.yaml").forEach { path ->
            shouldThrow<IllegalArgumentException> {
                DictionaryPackDescriptor(
                    schema = 1,
                    language = "pl",
                    displayName = "Bad",
                    wordCount = 1,
                    fldicAssetPath = path,
                    source = "x",
                    license = "MIT",
                )
            }
        }
    }

    test("rejects a wrongly-cased suffix, because the router is case-sensitive") {
        // "PL.FLDIC" is a binary dictionary that decodeFrequencies would hand to
        // the JSON parser, since it compares against the lower-case suffix. A
        // case-insensitive check here would wave through exactly the descriptor
        // that then fails to load.
        listOf("ime/dict/PL.FLDIC", "ime/dict/pl.Fldic", "ime/dict/pl.JSON").forEach { path ->
            shouldThrow<IllegalArgumentException> {
                DictionaryPackDescriptor(
                    schema = 1,
                    language = "pl",
                    displayName = "Bad",
                    wordCount = 1,
                    fldicAssetPath = path,
                    source = "x",
                    license = "MIT",
                )
            }
        }
    }

    test("accepts both formats the loader can actually decode") {
        // JSON is not a mistake: it is the first bundled path for every
        // language, and an addon shipping one decoded correctly before this
        // rule existed.
        listOf("ime/dict/pl.fldic", "ime/dict/pl.json").forEach { path ->
            DictionaryPackDescriptor(
                schema = 1,
                language = "pl",
                displayName = "Good",
                wordCount = 1,
                fldicAssetPath = path,
                source = "x",
                license = "MIT",
            ).fldicAssetPath shouldBe path
        }
    }

    test("rejects absolute asset paths to keep the loader inside the addon's assets") {
        shouldThrow<IllegalArgumentException> {
            DictionaryPackDescriptor(
                schema = 1,
                language = "pl",
                displayName = "Bad",
                wordCount = 1,
                fldicAssetPath = "/absolute/path.fldic",
                source = "x",
                license = "MIT",
            )
        }
    }

    test("rejects '..' path-traversal segments in asset paths") {
        shouldThrow<IllegalArgumentException> {
            DictionaryPackDescriptor(
                schema = 1,
                language = "pl",
                displayName = "Bad",
                wordCount = 1,
                fldicAssetPath = "ime/../../../etc/passwd",
                source = "x",
                license = "MIT",
            )
        }
        shouldThrow<IllegalArgumentException> {
            DictionaryPackDescriptor(
                schema = 1,
                language = "pl",
                displayName = "Bad",
                wordCount = 1,
                fldicAssetPath = "ime/dict/pl.fldic",
                zipfAssetPath = "freq\\..\\..\\secret.tsv",
                source = "x",
                license = "MIT",
            )
        }
    }

    test("allows a literal '..foo' filename segment that is not a parent-directory hop") {
        val desc = DictionaryPackDescriptor(
            schema = 1,
            language = "pl",
            displayName = "OK",
            wordCount = 1,
            fldicAssetPath = "ime/dict/..foo.fldic",
            source = "x",
            license = "MIT",
        )
        desc.fldicAssetPath shouldBe "ime/dict/..foo.fldic"
    }
})
