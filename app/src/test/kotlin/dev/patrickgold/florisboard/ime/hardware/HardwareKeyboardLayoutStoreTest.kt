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
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HardwareKeyboardLayoutStoreTest : FunSpec({
    test("importing a KLC layout stores and reloads a private catalog entry") {
        val root = Files.createTempDirectory("hardware-layouts").toFile()
        val file = root.resolve("hardware_keyboard_layouts.json")
        try {
            val store = HardwareKeyboardLayoutStore.forStorageFile(file, clock = { 123L })
            val result = store.importLayout(
                sourceName = "custom.klc",
                inputStream = ByteArrayInputStream(SampleKlc.toByteArray(Charsets.UTF_8)),
            )

            result.status shouldBe HardwareKeyboardLayoutImportStatus.Imported
            val imported = store.layouts().single()
            imported.displayName shouldBe "Custom Hardware"
            imported.sourceFormat shouldBe HardwareKeyboardLayoutSourceFormat.KLC
            imported.importedAtEpochMillis shouldBe 123L
            imported.layout.scancodeMap shouldContainKey 0x1E

            val reloaded = HardwareKeyboardLayoutStore.forStorageFile(file)
            val persisted = reloaded.layouts().single()
            persisted.id shouldBe imported.id
            persisted.layout.scancodeMap[0x1E].shouldNotBeNull().normal shouldBe "a".codePointAt(0)
        } finally {
            root.deleteRecursively()
        }
    }

    test("Keyman KMP import selects the first importable LDML layout") {
        val result = HardwareKeyboardLayoutImporter.importBytes(
            sourceName = "demo.kmp",
            bytes = kmpBytes(
                "kmp.json" to """{"info":{"name":"Demo Package","version":"1.0"}}""",
                "source/demo.xml" to """
                    <keyboard locale="am-ET">
                      <names><name value="Amharic Demo"/></names>
                      <keys><key id="A01" output="ሀ"/></keys>
                    </keyboard>
                """.trimIndent(),
            ),
            importedAtEpochMillis = 456L,
        )

        result.status shouldBe HardwareKeyboardLayoutImportStatus.Imported
        val imported = result.importedLayout.shouldNotBeNull()
        imported.displayName shouldBe "Amharic Demo"
        imported.sourceFormat shouldBe HardwareKeyboardLayoutSourceFormat.KEYMAN_LDML_PACKAGE
        imported.locale shouldBe "am-ET"
        imported.layout.scancodeMap.values.single().normal shouldBe "ሀ".codePointAt(0)
    }

    test("compiled-only Keyman packages are reported as having no importable layout") {
        val result = HardwareKeyboardLayoutImporter.importBytes(
            sourceName = "compiled.kmp",
            bytes = kmpBytes(
                "kmp.json" to """
                    {
                      "info": { "name": "Compiled", "version": "1.0" },
                      "files": [{ "name": "compiled.kmx" }]
                    }
                """.trimIndent(),
                "compiled.kmx" to "compiled",
            ),
            importedAtEpochMillis = 789L,
        )

        result.status shouldBe HardwareKeyboardLayoutImportStatus.NoImportableLayout
        result.importedLayout shouldBe null
    }

    test("delete removes one imported layout from the persisted catalog") {
        val root = Files.createTempDirectory("hardware-layouts").toFile()
        val file = root.resolve("hardware_keyboard_layouts.json")
        try {
            val store = HardwareKeyboardLayoutStore.forStorageFile(file)
            val imported = store.importLayout(
                sourceName = "custom.klc",
                inputStream = ByteArrayInputStream(SampleKlc.toByteArray(Charsets.UTF_8)),
            ).importedLayout.shouldNotBeNull()

            store.deleteLayout(imported.id) shouldBe true
            store.layouts() shouldHaveSize 0
            HardwareKeyboardLayoutStore.forStorageFile(file).layouts() shouldHaveSize 0
        } finally {
            root.deleteRecursively()
        }
    }
})

private val SampleKlc = """
KBD "CUSTOM" "Custom Hardware"
LOCALENAME "en-US"
SHIFTSTATE
0
1
LAYOUT
1E A 1 0061 0041
ENDKBD
""".trimIndent()

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
