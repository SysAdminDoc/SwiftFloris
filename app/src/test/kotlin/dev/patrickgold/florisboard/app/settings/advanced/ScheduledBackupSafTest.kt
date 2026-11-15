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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random

/** A backup folder stand-in, with the ways a provider has been seen to go wrong. */
private class FakeBackupFolder : BackupFolder<String> {
    val documents = linkedMapOf<String, ByteArray>()
    var dropOnWrite = 0
    var renameFails = false
    /** What the renamed document reads back as, for a provider whose rename hands back another copy. */
    var renamedReadsAs: ByteArray? = null
    private var renamedTo: String? = null

    override fun create(name: String): String {
        documents[name] = ByteArray(0)
        return name
    }

    override fun write(document: String, archive: File) {
        val bytes = archive.readBytes()
        documents[document] = bytes.copyOf(bytes.size - dropOnWrite)
    }

    override fun open(document: String): InputStream? {
        val bytes = renamedReadsAs?.takeIf { document == renamedTo } ?: documents[document]
        return bytes?.let(::ByteArrayInputStream)
    }

    override fun rename(document: String, name: String): String? {
        if (renameFails) return null
        documents[name] = checkNotNull(documents.remove(document))
        renamedTo = name
        return name
    }

    override fun delete(document: String) {
        documents.remove(document)
    }
}

class ScheduledBackupSafTest : FunSpec({
    val finalName = "swiftfloris-backup-1-1700000000000.sfbak"
    lateinit var root: File
    lateinit var archive: File
    lateinit var receipt: BackupReceipt

    beforeTest {
        root = Files.createTempDirectory("scheduled-backup").toFile()
        val bytes = Random(7).nextBytes(64 * 1024 + 13)
        archive = File(root, "archive.sfbak").also { it.writeBytes(bytes) }
        receipt = BackupReceipt(
            archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
            encrypted = true,
            sections = Backup.scheduledSelection(),
            byteSize = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        )
    }

    afterTest {
        root.deleteRecursively()
    }

    test("a checked archive ends up under its final name and nothing else is left") {
        val folder = FakeBackupFolder()
        ScheduledBackupSaf.publishTo(folder, archive, finalName, receipt) shouldBe finalName
        folder.documents.keys shouldContainExactly setOf(finalName)
        folder.documents.getValue(finalName).contentEquals(archive.readBytes()) shouldBe true
    }

    test("a short temporary write is deleted before it gets a backup's name") {
        val folder = FakeBackupFolder().apply { dropOnWrite = 10 }
        shouldThrow<BackupVerificationException> {
            ScheduledBackupSaf.publishTo(folder, archive, finalName, receipt)
        }.problem shouldBe BackupVerificationException.Problem.Truncated
        folder.documents.keys.shouldBeEmpty()
    }

    test("a renamed copy that reads back different is deleted") {
        val altered = archive.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
        val folder = FakeBackupFolder().apply { renamedReadsAs = altered }
        shouldThrow<BackupVerificationException> {
            ScheduledBackupSaf.publishTo(folder, archive, finalName, receipt)
        }.problem shouldBe BackupVerificationException.Problem.Altered
        folder.documents.keys.shouldBeEmpty()
    }

    test("a provider that can't rename is left with nothing") {
        val folder = FakeBackupFolder().apply { renameFails = true }
        shouldThrow<IllegalStateException> { ScheduledBackupSaf.publishTo(folder, archive, finalName, receipt) }
        folder.documents.keys.shouldBeEmpty()
    }

    test("retention deletes restorable archives past the newest ones it keeps") {
        val restorable = setOf("newest", "third", "fifth")
        val newestFirst = listOf("newest", "damaged", "third", "foreign", "fifth")
        ScheduledBackupSaf.retentionDeletions(newestFirst, keep = 2) { it in restorable } shouldContainExactly
            listOf("fifth")
    }

    test("retention never deletes or counts an archive that doesn't open") {
        val newestFirst = listOf("damaged", "good", "older")
        ScheduledBackupSaf.retentionDeletions(newestFirst, keep = 1) { it != "damaged" } shouldContainExactly
            listOf("older")
        ScheduledBackupSaf.retentionDeletions(newestFirst, keep = 2) { it != "damaged" }.shouldBeEmpty()
    }

    test("retention doesn't decrypt archives an earlier run already opened") {
        val opened = mutableListOf<String>()
        val plan = ScheduledBackupSaf.rememberingRetention(
            newestFirst = listOf("new", "a", "b", "c"),
            keep = 3,
            checkedKeys = setOf("new", "a", "b", "c"),
            keyOf = { it },
        ) { opened += it; true }
        opened.shouldBeEmpty()
        plan.deletions shouldContainExactly listOf("c")
        plan.checkedKeys shouldBe setOf("new", "a", "b")
    }

    test("retention remembers an archive that opens and checks a damaged one again next time") {
        val opened = mutableListOf<String>()
        val plan = ScheduledBackupSaf.rememberingRetention(
            newestFirst = listOf("new", "legacy", "damaged", "old"),
            keep = 2,
            checkedKeys = setOf("new", "gone"),
            keyOf = { it },
        ) { opened += it; it != "damaged" }
        opened shouldContainExactly listOf("legacy", "damaged", "old")
        plan.deletions shouldContainExactly listOf("old")
        plan.checkedKeys shouldBe setOf("new", "legacy")
    }

    test("retention opens nothing while the folder holds no more than it keeps") {
        val plan = ScheduledBackupSaf.rememberingRetention(
            newestFirst = listOf("new", "legacy"),
            keep = 3,
            checkedKeys = setOf("new", "gone"),
            keyOf = { it },
        ) { error("Nothing should be opened when nothing would be deleted.") }
        plan.deletions.shouldBeEmpty()
        plan.checkedKeys shouldBe setOf("new")
    }

    test("retention opens an archive again when its size changed under the same name") {
        // A file swapped in under a checked archive's name, or cut short by a sync, is a
        // different file. Remembering it by name alone would let it push a good backup out.
        val opened = mutableListOf<String>()
        val sizes = mapOf("new" to 100L, "replaced" to 7L, "old" to 100L)
        val plan = ScheduledBackupSaf.rememberingRetention(
            newestFirst = listOf("new", "replaced", "old"),
            keep = 1,
            checkedKeys = setOf("new/100", "replaced/100", "old/100"),
            keyOf = { ScheduledBackupSaf.checkedKey(it, sizes.getValue(it)) },
        ) { opened += it; it != "replaced" }
        opened shouldContainExactly listOf("replaced")
        plan.deletions shouldContainExactly listOf("old")
        plan.checkedKeys shouldBe setOf("new/100")
    }

    test("retention opens an archive every time when the folder doesn't report its size") {
        val opened = mutableListOf<String>()
        val plan = ScheduledBackupSaf.rememberingRetention(
            newestFirst = listOf("new", "unsized", "old"),
            keep = 2,
            checkedKeys = setOf("new/100", "old/100"),
            keyOf = { if (it == "unsized") ScheduledBackupSaf.checkedKey(it, -1L) else "$it/100" },
        ) { opened += it; true }
        opened shouldContainExactly listOf("unsized")
        plan.deletions shouldContainExactly listOf("old")
        plan.checkedKeys shouldBe setOf("new/100")
    }
})
