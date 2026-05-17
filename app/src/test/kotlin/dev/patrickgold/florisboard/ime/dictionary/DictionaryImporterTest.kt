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

package dev.patrickgold.florisboard.ime.dictionary

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DictionaryImporterTest : FunSpec({

    val importer = DictionaryImporter()

    test("imports a Gboard PersonalDictionary XML payload") {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <userdictionary>
                <entry word="omw" frequency="240" shortcut="omw" locale="en"/>
                <entry word="brb" frequency="220" shortcut="brb" locale="en"/>
                <entry word="gracias" frequency="200" locale="es"/>
            </userdictionary>
        """.trimIndent()

        val result = importer.parseGboardXml(xml)

        result shouldHaveSize 3
        result[0] shouldBe PersonalDictionaryEntry("omw", 240, "omw", "en")
        result[1] shouldBe PersonalDictionaryEntry("brb", 220, "brb", "en")
        result[2] shouldBe PersonalDictionaryEntry("gracias", 200, null, "es")
    }

    test("imports an XML payload with escaped entities") {
        // Gboard sometimes exports shortcut values that include `&` (rare
        // but possible). The parser must decode the XML entity safely.
        val xml = """
            <userdictionary>
                <entry word="r&amp;d" frequency="150" locale="en"/>
            </userdictionary>
        """.trimIndent()

        val result = importer.parseGboardXml(xml)

        result shouldHaveSize 1
        result[0].word shouldBe "r&d"
    }

    test("clamps frequency into the 0..255 byte range") {
        val xml = """
            <userdictionary>
                <entry word="too_high" frequency="9999"/>
                <entry word="too_low" frequency="-5"/>
                <entry word="missing_freq"/>
            </userdictionary>
        """.trimIndent()

        val result = importer.parseGboardXml(xml)

        result[0].frequency shouldBe 255
        result[1].frequency shouldBe 0
        // Defaults to 128 (mid-range) when frequency attr is absent.
        result[2].frequency shouldBe 128
    }

    test("imports a generic CSV payload") {
        val csv = """
            word,frequency,shortcut,locale
            omw,240,omw,en
            brb,220,brb,en
            gracias,200,,es
        """.trimIndent()

        val result = importer.parseCsv(csv)

        result shouldHaveSize 3
        result[0] shouldBe PersonalDictionaryEntry("omw", 240, "omw", "en")
        result[2] shouldBe PersonalDictionaryEntry("gracias", 200, null, "es")
    }

    test("imports quoted CSV fields containing commas and escaped quotes") {
        val csv = listOf(
            "word,frequency,shortcut,locale",
            "\"hello, world\",200,hw,en",
            "\"say \"\"yes\"\"\",180,yes,en",
        ).joinToString("\n")

        val result = importer.parseCsv(csv)

        result shouldHaveSize 2
        result[0] shouldBe PersonalDictionaryEntry("hello, world", 200, "hw", "en")
        result[1] shouldBe PersonalDictionaryEntry("say \"yes\"", 180, "yes", "en")
    }

    test("imports a CSV payload without a header row") {
        val csv = """
            yolo,200,,en
            fyi,180,fyi,en
        """.trimIndent()

        val result = importer.parseCsv(csv)

        result shouldHaveSize 2
        result[0].word shouldBe "yolo"
    }

    test("detects Gboard zip and extracts the XML inside") {
        // ROADMAP §7 Next-6.1 — end-to-end on a synthetic
        // PersonalDictionary.zip fixture matching what Google Takeout
        // produces (a single XML file inside a zip).
        val zipBytes = makeZip(
            "PersonalDictionary/Phrase Personalization Words.xml" to """
                <userdictionary>
                    <entry word="kek" frequency="200" locale="en"/>
                </userdictionary>
            """.trimIndent().toByteArray(Charsets.UTF_8),
        )

        val result = importer.import(ByteArrayInputStream(zipBytes))

        result shouldHaveSize 1
        result[0].word shouldBe "kek"
    }

    test("zip with no dictionary file raises a clear exception") {
        val zipBytes = makeZip(
            "README.txt" to "Nothing to import here".toByteArray(Charsets.UTF_8),
        )

        shouldThrow<DictionaryImportException> {
            importer.import(ByteArrayInputStream(zipBytes))
        }
    }

    test("FlorisBoard SQLite-snapshot backup raises a clear in-app routing hint") {
        // ROADMAP §7 Next-6.2 — .flbackup with a raw .db snapshot can't be
        // round-tripped JVM-side, but the user-facing error must tell the
        // user exactly where to find the in-app path.
        val zipBytes = makeZip(
            "backup.db" to ByteArray(16),
        )

        shouldThrow<DictionaryImportException> {
            importer.import(ByteArrayInputStream(zipBytes))
        }
    }

    test("unknown format raises a clear exception") {
        val random = ByteArrayInputStream(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))  // ELF magic

        shouldThrow<DictionaryImportException> {
            importer.import(random)
        }
    }

    test("detectFormat routes by structure, not file extension") {
        importer.detectFormat("PK\u0003\u0004".toByteArray()) shouldBe DictionaryImportFormat.ZIP
        importer.detectFormat("<?xml version=\"1.0\"?><userdictionary/>".toByteArray()) shouldBe
            DictionaryImportFormat.XML
        importer.detectFormat("word,frequency\nomw,240".toByteArray()) shouldBe
            DictionaryImportFormat.CSV
        importer.detectFormat("this is just prose, not a dictionary".toByteArray()) shouldBe
            DictionaryImportFormat.UNKNOWN
        importer.detectFormat("{ \"predictions\": [] }".toByteArray()) shouldBe
            DictionaryImportFormat.JSON
        importer.detectFormat("[ { \"word\": \"omw\" } ]".toByteArray()) shouldBe
            DictionaryImportFormat.JSON
    }

    // ROADMAP §6 N16.2 — SwiftKey `swiftkey-cloud.json` import tests.

    test("parseSwiftKeyJson: predictions+shortcuts envelope is the canonical case") {
        val json = """
            {
              "predictions": [
                { "word": "omw", "frequency": 240, "locale": "en" },
                { "word": "brb", "frequency": 220, "language": "en" }
              ],
              "shortcuts": [
                { "word": "thank you", "shortcut": "ty", "locale": "en" }
              ]
            }
        """.trimIndent()

        val result = importer.parseSwiftKeyJson(json)

        result shouldHaveSize 3
        result[0] shouldBe PersonalDictionaryEntry("omw", 240, null, "en")
        result[1] shouldBe PersonalDictionaryEntry("brb", 220, null, "en")
        result[2] shouldBe PersonalDictionaryEntry("thank you", 128, "ty", "en")
    }

    test("parseSwiftKeyJson: user_data envelope wrapping") {
        val json = """
            {
              "user_data": {
                "predictions": [
                  { "text": "gracias", "frequency": 200, "lang": "es" }
                ]
              }
            }
        """.trimIndent()

        val result = importer.parseSwiftKeyJson(json)

        result shouldHaveSize 1
        result[0] shouldBe PersonalDictionaryEntry("gracias", 200, null, "es")
    }

    test("parseSwiftKeyJson: bare array of entries") {
        val json = """[
            { "word": "omw", "frequency": 240, "locale": "en" },
            { "word": "tomorrow", "frequency": 180, "locale": "en" }
        ]"""

        val result = importer.parseSwiftKeyJson(json)

        result shouldHaveSize 2
        result[0] shouldBe PersonalDictionaryEntry("omw", 240, null, "en")
        result[1] shouldBe PersonalDictionaryEntry("tomorrow", 180, null, "en")
    }

    test("parseSwiftKeyJson: tolerates missing frequency and locale (defaults to 128 / null)") {
        val json = """{ "words": [{ "word": "hello" }] }"""

        val result = importer.parseSwiftKeyJson(json)

        result shouldHaveSize 1
        result[0] shouldBe PersonalDictionaryEntry("hello", 128, null, null)
    }

    test("parseSwiftKeyJson: clamps out-of-range frequency to [0,255]") {
        val json = """[
            { "word": "low", "frequency": -50 },
            { "word": "high", "frequency": 9999 }
        ]"""

        val result = importer.parseSwiftKeyJson(json)

        result shouldHaveSize 2
        result[0].frequency shouldBe 0
        result[1].frequency shouldBe 255
    }

    test("parseSwiftKeyJson: malformed JSON returns empty list (not throw)") {
        // FlorisBoard backup manifest JSON sitting in the same zip alongside
        // CSV/XML must not abort the overall import. The parser silently
        // returns an empty list when the JSON is bad OR when it has no
        // recognisable entries.
        val result = importer.parseSwiftKeyJson("{ this is not json")
        result shouldHaveSize 0
    }

    test("parseSwiftKeyJson: empty array/object yields no entries") {
        importer.parseSwiftKeyJson("[]") shouldHaveSize 0
        importer.parseSwiftKeyJson("{}") shouldHaveSize 0
        importer.parseSwiftKeyJson("{ \"predictions\": [] }") shouldHaveSize 0
    }

    test("parseSwiftKeyJson: drops entries with blank or missing word field") {
        val json = """[
            { "word": "" },
            { "word": "   " },
            { "frequency": 128 },
            { "word": "valid", "frequency": 200 }
        ]"""

        val result = importer.parseSwiftKeyJson(json)

        result shouldHaveSize 1
        result[0].word shouldBe "valid"
    }

    test("import(): detects a bare SwiftKey JSON stream and routes it") {
        val json = """
            { "predictions": [{ "word": "swiftkeyport", "frequency": 200, "locale": "en" }] }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val result = importer.import(ByteArrayInputStream(json))

        result shouldHaveSize 1
        result[0] shouldBe PersonalDictionaryEntry("swiftkeyport", 200, null, "en")
    }

    test("parseSwiftKeyJson rejects pathologically deep envelopes") {
        val nested = buildString {
            repeat(70) { append("""{"wrap":""") }
            append("""{"word":"too-deep"}""")
            repeat(70) { append("}") }
        }

        shouldThrow<DictionaryImportException> {
            importer.parseSwiftKeyJson(nested)
        }
    }

    test("zip import parses SwiftKey JSON entries") {
        val zipBytes = makeZip(
            "swiftkey-cloud.json" to """{ "words": [{ "word": "portable", "frequency": 200 }] }"""
                .toByteArray(Charsets.UTF_8),
        )

        val result = importer.import(ByteArrayInputStream(zipBytes))

        result shouldHaveSize 1
        result[0].word shouldBe "portable"
    }

    // Hardening: a UTF-8 BOM in front of a JSON / XML / CSV payload would
    // previously route to UNKNOWN because the BOM character survives
    // `trimStart()`. Detection should strip the BOM before pattern-matching.
    test("detectFormat strips a UTF-8 BOM before classifying") {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        importer.detectFormat(bom + "<?xml version=\"1.0\"?><userdictionary/>".toByteArray()) shouldBe
            DictionaryImportFormat.XML
        importer.detectFormat(bom + "{ \"words\": [] }".toByteArray()) shouldBe
            DictionaryImportFormat.JSON
        importer.detectFormat(bom + "word,frequency\nomw,240".toByteArray()) shouldBe
            DictionaryImportFormat.CSV
    }

    // Regression: the old header-detection heuristic dropped the first entry
    // when a user's dictionary literally contained the word "word".
    test("parseCsv preserves the word 'word' when no header row is present") {
        val csv = "word,200,wd,en\nfollowup,150,,en"

        val result = importer.parseCsv(csv)

        result shouldHaveSize 2
        result[0] shouldBe PersonalDictionaryEntry("word", 200, "wd", "en")
        result[1] shouldBe PersonalDictionaryEntry("followup", 150, null, "en")
    }

    test("parseGboardXml decodes decimal and hex numeric entities") {
        val xml = """
            <userdictionary>
                <entry word="caf&#233;" frequency="200"/>
                <entry word="A&#x42;C" frequency="180"/>
            </userdictionary>
        """.trimIndent()

        val result = importer.parseGboardXml(xml)

        result shouldHaveSize 2
        result[0].word shouldBe "café"
        result[1].word shouldBe "ABC"
    }
})

private fun makeZip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zos ->
        for ((name, bytes) in entries) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(bytes)
            zos.closeEntry()
        }
    }
    return out.toByteArray()
}
