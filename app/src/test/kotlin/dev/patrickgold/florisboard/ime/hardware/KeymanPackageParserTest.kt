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

package dev.patrickgold.florisboard.ime.hardware

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KeymanPackageParserTest : FunSpec({
    test("parses kmp metadata and classifies compiled keyboard packages") {
        val pkg = KeymanPackageParser.parse(
            kmpBytes(
                "kmp.json" to """
                    {
                      "info": {
                        "name": "Geez",
                        "version": "1.2",
                        "author": "Example"
                      },
                      "options": {
                        "readmeFile": "readme.htm",
                        "welcomeFile": "welcome.htm"
                      },
                      "files": [
                        { "name": "gff_geez.kmx", "description": "Keyboard" },
                        { "name": "readme.htm", "description": "Readme" }
                      ],
                      "keyboards": [
                        {
                          "id": "gff_geez",
                          "name": "GFF Geez",
                          "version": "1.0",
                          "rtl": false,
                          "languages": [{ "id": "am", "name": "Amharic" }],
                          "examples": [{ "id": "am", "keys": "s a", "text": "ሳ" }]
                        }
                      ]
                    }
                """.trimIndent(),
                "gff_geez.kmx" to "compiled",
                "readme.htm" to "<p>Readme</p>",
            ),
        )

        pkg.status shouldBe KeymanPackageImportStatus.CompiledEngineRequired
        pkg.info.name shouldBe "Geez"
        pkg.info.version shouldBe "1.2"
        pkg.options.readmeFile shouldBe "readme.htm"
        pkg.keyboards.single().languages.single().id shouldBe "am"
        pkg.keyboards.single().examples.single().text shouldBe "ሳ"
        pkg.files.map { it.fileType } shouldBe listOf(
            KeymanPackageFileType.CompiledKeyboard,
            KeymanPackageFileType.Documentation,
        )
    }

    test("extracts LDML XML layouts when a package includes importable keyboard XML") {
        val pkg = KeymanPackageParser.parse(
            kmpBytes(
                "kmp.json" to """
                    {
                      "info": { "name": "LDML Demo", "version": "1.0" },
                      "files": [{ "name": "source/demo.xml", "description": "LDML" }]
                    }
                """.trimIndent(),
                "source/demo.xml" to """
                    <keyboard locale="am-ET">
                      <names><name value="Amharic Demo"/></names>
                      <keys><key id="A01" output="ሀ" longPress="ሁ"/></keys>
                    </keyboard>
                """.trimIndent(),
            ),
        )

        pkg.status shouldBe KeymanPackageImportStatus.LdmlReady
        pkg.ldmlLayouts shouldHaveSize 1
        pkg.ldmlLayouts.single().entryName shouldBe "source/demo.xml"
        pkg.ldmlLayouts.single().layout.name shouldBe "Amharic Demo"
        pkg.ldmlLayouts.single().layout.scancodeMap.values.single().normal shouldBe "ሀ".codePointAt(0)
    }

    test("classifies lexical model packages separately from keyboards") {
        val pkg = KeymanPackageParser.parse(
            kmpBytes(
                "kmp.json" to """
                    {
                      "info": { "name": "Lexical", "version": "2.0" },
                      "lexicalModels": [
                        {
                          "id": "example.model",
                          "name": "Example Model",
                          "version": "2.0",
                          "languages": [{ "id": "en", "name": "English" }]
                        }
                      ]
                    }
                """.trimIndent(),
                "example.model.js" to "model",
            ),
        )

        pkg.status shouldBe KeymanPackageImportStatus.LexicalModelOnly
        pkg.lexicalModels.single().languages.single().name shouldBe "English"
    }

    test("flags mixed keyboard and lexical model metadata as unsupported") {
        val pkg = KeymanPackageParser.parse(
            kmpBytes(
                "kmp.json" to """
                    {
                      "info": { "name": "Mixed", "version": "1.0" },
                      "keyboards": [{ "id": "kbd", "name": "Keyboard" }],
                      "lexicalModels": [{ "id": "model", "name": "Model", "languages": [] }]
                    }
                """.trimIndent(),
            ),
        )

        pkg.status shouldBe KeymanPackageImportStatus.MixedPackageUnsupported
    }

    test("flags mixed keyboard and lexical model package entries as unsupported") {
        val pkg = KeymanPackageParser.parse(
            kmpBytes(
                "kmp.json" to """
                    {
                      "info": { "name": "Mixed entries", "version": "1.0" },
                      "files": [
                        { "name": "keyboard.kmx" },
                        { "name": "model.model.js" }
                      ]
                    }
                """.trimIndent(),
                "keyboard.kmx" to "compiled",
                "model.model.js" to "model",
            ),
        )

        pkg.status shouldBe KeymanPackageImportStatus.MixedPackageUnsupported
    }

    test("skips unsafe zip entries before later extraction code can trust paths") {
        val pkg = KeymanPackageParser.parse(
            kmpBytes(
                "kmp.json" to """{ "info": { "name": "Unsafe", "version": "1.0" } }""",
                "../evil.xml" to "<keyboard/>",
                "/absolute.xml" to "<keyboard/>",
                "safe/readme.htm" to "<p>safe</p>",
            ),
        )

        pkg.entries.map { it.name } shouldBe listOf("kmp.json", "safe/readme.htm")
        pkg.warnings.shouldNotBeEmpty()
    }

    test("returns Invalid for non-zip bytes") {
        val pkg = KeymanPackageParser.parse("not a zip".toByteArray())

        pkg.status shouldBe KeymanPackageImportStatus.Invalid
        pkg.warnings.shouldNotBeEmpty()
    }
})

private fun kmpBytes(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
