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

/**
 * Backup coverage used to live in three places that could disagree: the archive selector,
 * `backup_rules.xml`, and `data_extraction_rules.xml`. These assertions bind Android's rule files
 * to [BackupDataInventory] in both directions, so a store cannot be added without deciding what
 * happens to it, and a rule cannot linger for a store that no longer exists.
 */
class BackupDataInventoryTest : FunSpec({

    fun readRules(name: String): String {
        val candidates = listOf(
            "app/src/main/res/xml/$name",
            "src/main/res/xml/$name",
        )
        val file = candidates.map(::File).firstOrNull { it.isFile && it.canRead() }
            ?: error("$name not reachable from ${File(".").absolutePath}")
        return file.readText()
    }

    fun excludesIn(xml: String, ruleSet: String): Set<Pair<String, String>> {
        val section = Regex("<$ruleSet>(.*?)</$ruleSet>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?: error("<$ruleSet> block missing")
        return Regex(
            """<exclude\s+domain="([^"]+)"\s+path="([^"]+)"\s*/>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(section.replace(Regex("\\s+"), " "))
            .map { it.groupValues[1] to it.groupValues[2] }
            .toSet()
    }

    fun includesIn(xml: String, ruleSet: String): Set<Pair<String, String>> {
        val section = Regex("<$ruleSet>(.*?)</$ruleSet>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?: error("<$ruleSet> block missing")
        return Regex(
            """<include\s+domain="([^"]+)"\s+path="([^"]+)"\s*/>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(section.replace(Regex("\\s+"), " "))
            .map { it.groupValues[1] to it.groupValues[2] }
            .toSet()
    }

    /** WAL / SHM / journal companions are rule detail, not separate stores. */
    fun canonical(domain: String, path: String): Pair<String, String> {
        if (domain != "database") return domain to path
        val base = path.removeSuffix("-wal").removeSuffix("-shm").removeSuffix("-journal")
        return domain to base
    }

    test("every persisted store has a disposition and a unique id") {
        BackupDataInventory.entries.map { it.id }.toSet().size shouldBe BackupDataInventory.entries.size
        BackupDataInventory.entries.filter { it.path.isBlank() }.shouldBeEmpty()
    }

    test("cloud-backup rules exclude exactly the stores the inventory holds back") {
        val xml = readRules("data_extraction_rules.xml")
        val declared = excludesIn(xml, "cloud-backup").map { canonical(it.first, it.second) }.toSet()

        declared shouldContainExactlyInAnyOrder BackupDataInventory.requiredAndroidExcludes()
    }

    test("device-transfer rules match the cloud-backup rules") {
        val xml = readRules("data_extraction_rules.xml")
        val cloud = excludesIn(xml, "cloud-backup").map { canonical(it.first, it.second) }.toSet()
        val transfer = excludesIn(xml, "device-transfer").map { canonical(it.first, it.second) }.toSet()

        transfer shouldContainExactlyInAnyOrder cloud
    }

    test("the pre-31 allowlist includes nothing the inventory holds back") {
        val xml = readRules("backup_rules.xml").replace(Regex("\\s+"), " ")
        val includes = Regex("""<include domain="([^"]+)" path="([^"]+)"\s*/>""")
            .findAll(xml)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toSet()

        // The old rule file has no <exclude> support worth relying on, so it must be an allowlist
        // of included stores only.
        val included = setOf(
            "root" to "jetpref_datastore",
            "file" to "ime",
            "file" to "keypress_sounds",
            "file" to "sticker_packs",
            "file" to "snippets",
            "file" to "hardware_keyboard_layouts.json",
            "file" to "custom_emoji_tags.json",
            "file" to "emoji_pin_groups.json",
        )
        val heldBack = BackupDataInventory.entries
            .filter { it.disposition != BackupDisposition.Included }
            .map { it.domain.xmlName to it.path }
            .toSet()
        includes.intersect(heldBack).shouldBeEmpty()
        includes shouldContainExactlyInAnyOrder included
    }

    test("Android 12+ include rules carry every manual included store") {
        val expected = setOf(
            "root" to "jetpref_datastore",
            "file" to "ime",
            "file" to "keypress_sounds",
            "file" to "sticker_packs",
            "file" to "snippets",
            "file" to "hardware_keyboard_layouts.json",
            "file" to "custom_emoji_tags.json",
            "file" to "emoji_pin_groups.json",
        )
        val xml = readRules("data_extraction_rules.xml")
        includesIn(xml, "cloud-backup") shouldContainExactlyInAnyOrder expected
        includesIn(xml, "device-transfer") shouldContainExactlyInAnyOrder expected
    }

    test("every manual archive section maps to at least one inventory entry") {
        BackupDataInventory.coveredSections() shouldContainExactlyInAnyOrder BackupSection.entries.toSet()
    }

    test("stores the archive does not carry are enumerated rather than assumed") {
        BackupDataInventory.notYetCovered().shouldBeEmpty()
    }

    test("every store an archive omits carries a user-facing label") {
        // The Backup screen renders its "what an archive leaves behind" card
        // from these labels. A store added without one would be silently
        // dropped from that list, which is how the old hand-written sentence
        // ended up naming four of the thirteen.
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
