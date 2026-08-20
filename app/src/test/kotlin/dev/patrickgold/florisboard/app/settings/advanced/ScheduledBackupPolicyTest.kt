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
import io.kotest.matchers.shouldBe
import java.util.UUID

class ScheduledBackupPolicyTest : FunSpec({
    test("scheduled archive names are exact and timestamp sortable") {
        val id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
        val name = ScheduledBackupPolicy.archiveName(42, 1_234L, id)

        ScheduledBackupPolicy.isManagedArchive(name) shouldBe true
        ScheduledBackupPolicy.timestampFromArchiveName(name) shouldBe 1_234L
        ScheduledBackupPolicy.isManagedArchive("backup_io.github.other_42_1234.sfbak") shouldBe false
        ScheduledBackupPolicy.timestampFromArchiveName("$name.tmp") shouldBe null
    }

    test("retention choices are bounded to the UI contract") {
        ScheduledBackupPolicy.RetentionOptions shouldBe listOf(1, 3, 5, 10)
        ScheduledBackupPolicy.normalizeRetention(0) shouldBe 1
        ScheduledBackupPolicy.normalizeRetention(4) shouldBe 3
        ScheduledBackupPolicy.normalizeRetention(9) shouldBe 10
        ScheduledBackupCadence.fromId("weekly") shouldBe ScheduledBackupCadence.WEEKLY
        ScheduledBackupCadence.fromId("future") shouldBe ScheduledBackupCadence.DAILY
    }

    test("scheduled selection carries canonical stores but excludes clipboard history") {
        val selection = Backup.scheduledSelection()

        selection.jetprefDatastore shouldBe true
        selection.imeKeyboard shouldBe true
        selection.keypressSounds shouldBe true
        selection.imeTheme shouldBe true
        selection.localStickerPacks shouldBe true
        selection.snippets shouldBe true
        selection.hardwareKeyboardLayouts shouldBe true
        selection.customEmojiTags shouldBe true
        selection.emojiPinGroups shouldBe true
        selection.containsClipboard shouldBe false
    }

})
