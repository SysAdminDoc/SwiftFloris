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

package dev.patrickgold.florisboard.app.settings.advanced

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val AndroidXmlNamespace = "http://schemas.android.com/apk/res/android"

private data class AndroidBackupRule(
    val domain: String,
    val path: String,
    val requiredFlags: String? = null,
)

private fun locateProjectFile(path: String): File {
    val candidates = listOf(
        File(path),
        File(path.removePrefix("app/")),
    )
    return candidates.firstOrNull { it.isFile && it.canRead() }
        ?: error("$path not reachable from ${File(".").absolutePath}")
}

private fun locateProjectDirectory(path: String): File {
    val candidates = listOf(
        File(path),
        File(path.removePrefix("app/")),
    )
    return candidates.firstOrNull { it.isDirectory && it.canRead() }
        ?: error("$path not reachable from ${File(".").absolutePath}")
}

private fun parseProjectXml(path: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }
    return factory.newDocumentBuilder().parse(locateProjectFile(path))
}

private fun Document.elements(tagName: String): List<Element> {
    val nodes = getElementsByTagName(tagName)
    return (0 until nodes.length).map { nodes.item(it) as Element }
}

private fun Element.directChildren(tagName: String): List<Element> {
    return (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }
        .filter { it.tagName == tagName }
}

private fun Document.singleElement(tagName: String): Element {
    return elements(tagName).singleOrNull() ?: error("Expected exactly one <$tagName> element")
}

private fun Document.rulesIn(sectionName: String, ruleName: String): Set<AndroidBackupRule> {
    return singleElement(sectionName).directChildren(ruleName).map { element ->
        AndroidBackupRule(
            domain = element.getAttribute("domain"),
            path = element.getAttribute("path"),
            requiredFlags = element.getAttribute("requireFlags").ifBlank { null },
        )
    }.toSet()
}

private fun canonical(rule: AndroidBackupRule): Pair<String, String> {
    if (rule.domain != "database") return rule.domain to rule.path
    val base = rule.path.removeSuffix("-wal").removeSuffix("-shm").removeSuffix("-journal")
    return rule.domain to base
}

private fun expectedPortableAndroidPaths(): Set<Pair<String, String>> {
    return BackupDataInventory.entries
        .filter { it.disposition == BackupDisposition.Included }
        .map { entry ->
            val path = if (entry.domain == BackupDomain.File && entry.path.startsWith("ime/")) {
                "ime"
            } else {
                entry.path
            }
            entry.domain.xmlName to path
        }
        .toSet()
}

private fun discoveredSharedPreferenceFiles(sources: Map<String, String>): Set<String> {
    val directCall = Regex(
        """getSharedPreferences\(\s*(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_]*)\s*,""",
    )
    val cryptoCall = Regex(
        """TinkStringPreferenceCrypto\.sharedPreferences\(\s*.*?,\s*(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_]*)\s*,?\s*\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    val constant = Regex("""const\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*\"([^\"]+)\"""")
    val discovered = mutableSetOf<String>()

    sources.forEach { (path, source) ->
        if (path.endsWith("TinkStringPreferenceCrypto.kt")) return@forEach
        val constants = constant.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
        val expressions = buildList {
            directCall.findAll(source).forEach { add(it.groupValues[1]) }
            cryptoCall.findAll(source).forEach { add(it.groupValues[1]) }
        }
        expressions.forEach { expression ->
            val name = if (expression.startsWith('"')) {
                expression.removeSurrounding("\"")
            } else {
                constants[expression]
                    ?: error("Persisted SharedPreferences name $expression in $path is not a local constant")
            }
            discovered += "$name.xml"
        }
    }
    return discovered
}

private fun mainKotlinSources(): Map<String, String> {
    val root = locateProjectDirectory("app/src/main/kotlin")
    return root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
}

/**
 * These assertions bind the canonical inventory to every Android backup resource selected from
 * API 26 through Android 16 QPR2. They also discover SharedPreferences declarations in source so
 * a newly persisted store cannot inherit Android's permissive default without classification.
 */
class BackupDataInventoryTest : FunSpec({
    val expectedPortable = expectedPortableAndroidPaths()

    test("every persisted store has a disposition and a unique id") {
        BackupDataInventory.entries.map { it.id }.toSet().size shouldBe BackupDataInventory.entries.size
        BackupDataInventory.entries.filter { it.path.isBlank() }.shouldBeEmpty()
    }

    test("API 26 and 27 select an explicit export-nothing rule set") {
        val rules = parseProjectXml("app/src/main/res/xml/backup_rules.xml")
        rules.documentElement.tagName shouldBe "full-backup-content"
        rules.rulesIn("full-backup-content", "include").shouldBeEmpty()
        rules.rulesIn("full-backup-content", "exclude")
            .map { it.domain to it.path }
            .toSet() shouldContainExactlyInAnyOrder setOf(
            "root" to ".",
            "file" to ".",
            "database" to ".",
            "sharedpref" to ".",
            "external" to ".",
            "device_root" to ".",
            "device_file" to ".",
            "device_database" to ".",
            "device_sharedpref" to ".",
        )
    }

    test("API 28 through 30 select only portable stores with client-side encryption") {
        val rules = parseProjectXml("app/src/main/res/xml-v28/backup_rules.xml")
        rules.documentElement.tagName shouldBe "full-backup-content"
        val includes = rules.rulesIn("full-backup-content", "include")
        includes.map { it.domain to it.path }.toSet() shouldContainExactlyInAnyOrder expectedPortable
        includes.map { it.requiredFlags }.toSet() shouldBe setOf("clientSideEncryption")
    }

    test("API 31 and newer cloud backup requires encryption and carries only portable stores") {
        val rules = parseProjectXml("app/src/main/res/xml/data_extraction_rules.xml")
        rules.documentElement.tagName shouldBe "data-extraction-rules"
        rules.singleElement("cloud-backup").getAttribute("disableIfNoEncryptionCapabilities") shouldBe "true"
        rules.rulesIn("cloud-backup", "include")
            .map { it.domain to it.path }
            .toSet() shouldContainExactlyInAnyOrder expectedPortable
        rules.rulesIn("cloud-backup", "exclude")
            .map(::canonical)
            .toSet() shouldContainExactlyInAnyOrder BackupDataInventory.requiredAndroidExcludes()
    }

    test("API 31 and newer device transfer carries only portable stores") {
        val rules = parseProjectXml("app/src/main/res/xml/data_extraction_rules.xml")
        rules.rulesIn("device-transfer", "include")
            .map { it.domain to it.path }
            .toSet() shouldContainExactlyInAnyOrder expectedPortable
        rules.rulesIn("device-transfer", "exclude")
            .map(::canonical)
            .toSet() shouldContainExactlyInAnyOrder BackupDataInventory.requiredAndroidExcludes()
    }

    test("cross-platform transfer declares no placeholder iOS identity") {
        val rules = parseProjectXml("app/src/main/res/xml/data_extraction_rules.xml")
        rules.elements("cross-platform-transfer").shouldBeEmpty()
        rules.elements("platform-specific-params").shouldBeEmpty()
    }

    test("every resource variant selected from API 26 through Android 16 QPR2 is parsed") {
        val resRoot = locateProjectDirectory("app/src/main/res")
        val selectedFiles = resRoot.walkTopDown()
            .filter { it.isFile && it.name in setOf("backup_rules.xml", "data_extraction_rules.xml") }
            .map { it.relativeTo(resRoot).invariantSeparatorsPath }
            .toSet()
        selectedFiles shouldContainExactlyInAnyOrder setOf(
            "xml/backup_rules.xml",
            "xml-v28/backup_rules.xml",
            "xml/data_extraction_rules.xml",
        )
        selectedFiles.forEach { relativePath ->
            parseProjectXml("app/src/main/res/$relativePath").documentElement.tagName.isBlank() shouldBe false
        }
    }

    test("manifest delegates Android-managed backup to the fail-closed agent") {
        val manifest = parseProjectXml("app/src/main/AndroidManifest.xml")
        val application = manifest.singleElement("application")
        application.getAttributeNS(AndroidXmlNamespace, "allowBackup") shouldBe "true"
        application.getAttributeNS(AndroidXmlNamespace, "backupAgent") shouldBe
            "dev.patrickgold.florisboard.backup.SwiftFlorisBackupAgent"
        application.getAttributeNS(AndroidXmlNamespace, "fullBackupOnly") shouldBe "true"
    }

    test("every declared SharedPreferences file is classified by the inventory") {
        val discovered = discoveredSharedPreferenceFiles(mainKotlinSources())
        val registered = BackupDataInventory.entries
            .filter { it.domain == BackupDomain.SharedPref }
            .map { it.path }
            .toSet()
        (discovered - registered).shouldBeEmpty()
    }

    test("persisted-store discovery exposes an unregistered SharedPreferences fixture") {
        val fixture = mapOf(
            "Fixture.kt" to
                """context.getSharedPreferences("unregistered_store", Context.MODE_PRIVATE)""",
        )
        val registered = BackupDataInventory.entries
            .filter { it.domain == BackupDomain.SharedPref }
            .map { it.path }
            .toSet()
        (discoveredSharedPreferenceFiles(fixture) - registered) shouldBe setOf("unregistered_store.xml")
    }

    test("every manual archive section maps to at least one inventory entry") {
        BackupDataInventory.coveredSections() shouldContainExactlyInAnyOrder BackupSection.entries.toSet()
    }

    test("stores the archive does not carry are enumerated rather than assumed") {
        BackupDataInventory.notYetCovered().shouldBeEmpty()
    }

    test("every store an archive omits carries a user-facing label") {
        BackupDataInventory.archiveOmissions()
            .filter { it.omissionLabel == null }
            .map { it.id }
            .shouldBeEmpty()
    }

    test("archive omissions exclude the stores a section can carry") {
        BackupDataInventory.archiveOmissions()
            .filter { it.section != null }
            .map { it.id }
            .shouldBeEmpty()
    }

    test("sensitive exclusions all carry an explicit Android rule") {
        BackupDataInventory.sensitiveExclusions()
            .filterNot { it.requiresAndroidExclude }
            .shouldBeEmpty()
    }
})
