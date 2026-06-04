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

package dev.patrickgold.florisboard.resources

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationCopyTest : FunSpec({
    val rootStrings = readStringsXml("app/src/main/res/values/strings.xml")
    val turkishStrings = readStringsXml("app/src/main/res/values-tr/strings.xml")

    test("Turkish strings avoid adjacent repeated native words") {
        val duplicateFindings = turkishStrings.flatMap { (name, text) ->
            adjacentRepeatedWords(text).map { "$name: $it" }
        }

        duplicateFindings.shouldBeEmpty()
    }

    test("English source labels name the source instead of generic storage") {
        rootStrings["pref__theme__source_assets"] shouldBe "Bundled SwiftFloris assets"
        rootStrings["pref__theme__source_internal"] shouldBe "App-private storage"
        rootStrings["pref__theme__source_external"] shouldBe "External document provider"
        rootStrings["settings__udm__import_summary__format__csv"] shouldBe "CSV dictionary file"
        rootStrings["settings__udm__import_summary__format__zip"] shouldBe "ZIP dictionary archive"
        rootStrings["settings__udm__import_summary__format__unknown"] shouldBe
            "Legacy personal dictionary file"
    }

    test("trust-sensitive failure copy states unchanged data and details") {
        rootStrings["backup_and_restore__back_up__failure"] shouldContain
            "No backup archive was saved. Details: {error_message}"
        rootStrings["backup_and_restore__restore__failure"] shouldContain
            "Restore stopped before all selected data was imported. Details: {error_message}"
        rootStrings["settings__udm__dictionary_import_failure"] shouldContain
            "No dictionary changes were saved. Details: {error_message}"
        rootStrings["settings__udm__dictionary_export_failure"] shouldContain
            "No dictionary file was saved. Details: {error_message}"
        rootStrings["ext__export__failure"] shouldContain
            "No extension archive was saved. Details: {error_message}"
        rootStrings["ext__import__failure"] shouldContain
            "Installed extensions were not changed. Details: {error_message}"
    }

    test("generic destructive confirmation states device-local removal") {
        rootStrings["action__delete_confirm_message"] shouldContain
            "permanently removes the item from this device"
    }
})

private fun adjacentRepeatedWords(text: String): List<String> {
    val words = WordRegex.findAll(text.replace(PlaceholderRegex, " "))
        .map { it.value.lowercase(TurkishLocale) }
        .toList()
    return words.zipWithNext()
        .filter { (previous, next) -> previous == next }
        .map { (word, _) -> word }
}

private fun readStringsXml(path: String): Map<String, String> {
    val file = locateProjectFile(path)
    val document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(file)
    val nodes = document.getElementsByTagName("string")
    return buildMap {
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            put(name, node.textContent.trim())
        }
    }
}

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}

private val PlaceholderRegex = Regex("\\{[^}]+}")
private val WordRegex = Regex("[\\p{L}\\p{M}]+")
private val TurkishLocale = Locale.forLanguageTag("tr")
