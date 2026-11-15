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

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The parts of the backup check that need Android types: clipboard entries and messages. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupArchiveChecksTest {
    private lateinit var contents: File

    private val imagesOnly = Backup.FilesSelection(
        jetprefDatastore = false,
        imeKeyboard = false,
        keypressSounds = false,
        imeTheme = false,
        localStickerPacks = false,
        snippets = false,
        hardwareKeyboardLayouts = false,
        customEmojiTags = false,
        emojiPinGroups = false,
        clipboardTextItems = false,
        clipboardImageItems = true,
        clipboardVideoItems = false,
    )

    @Before
    fun setUp() {
        contents = Files.createTempDirectory("backup-contents").toFile()
    }

    @After
    fun tearDown() {
        contents.deleteRecursively()
    }

    private fun clipboardDir() = contents.subDir("clipboard").also { it.mkdirs() }

    private fun imageItem(uri: Uri) = ClipboardItem(
        type = ItemType.IMAGE,
        text = null,
        uri = uri,
        creationTimestampMs = 1L,
        isPinned = false,
        mimeTypes = listOf("image/png"),
    )

    private fun archiveImages(vararg items: ClipboardItem) {
        clipboardDir().subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME).writeJson(items.toList())
    }

    private fun archiveMedia(id: Long) {
        clipboardDir().subDir(ClipboardFileStorage.CLIPBOARD_FILES_PATH).also { it.mkdirs() }
            .subFile(id.toString()).writeBytes(ByteArray(16))
    }

    @Test
    fun imageEntryWithItsMediaFilePasses() {
        archiveImages(imageItem(ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, 7)))
        archiveMedia(7)
        shouldNotThrowAny { BackupArchiveContents.requireClipboardRestorable(contents, imagesOnly) }
    }

    @Test
    fun imageEntryWhoseMediaFileIsMissingFails() {
        archiveImages(imageItem(ContentUris.withAppendedId(ClipboardMediaProvider.IMAGE_CLIPS_URI, 7)))
        shouldThrow<IllegalStateException> {
            BackupArchiveContents.requireClipboardRestorable(contents, imagesOnly)
        }
    }

    @Test
    fun imageEntryWithoutAProviderIdFails() {
        archiveImages(imageItem(Uri.parse("content://elsewhere/not-a-number")))
        archiveMedia(7)
        shouldThrow<IllegalStateException> {
            BackupArchiveContents.requireClipboardRestorable(contents, imagesOnly)
        }
    }

    @Test
    fun clipboardListThatIsNotJsonFails() {
        clipboardDir().subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME).writeText("{not json")
        shouldThrowAny { BackupArchiveContents.requireClipboardRestorable(contents, imagesOnly) }
    }

    @Test
    fun failedManualSaveSaysWhatWentWrongAndWhatItLeftBehind() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val error = ManualBackupSaveException(
            FailedSaveCleanup.Removed,
            BackupVerificationException(BackupVerificationException.Problem.Truncated),
        )
        error.backupFailureMessage(context, "fallback") shouldBe
            context.getString(R.string.backup_and_restore__verify__truncated) + " " +
            context.getString(R.string.backup_and_restore__back_up__cleanup_removed)
    }
}
