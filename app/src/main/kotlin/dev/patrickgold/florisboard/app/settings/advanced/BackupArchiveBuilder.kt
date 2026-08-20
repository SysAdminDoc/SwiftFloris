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
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.media.sticker.LocalStickerPackRepository
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson
import java.io.File

/** Result of constructing a manual or scheduled archive in app-private storage. */
internal data class BuiltBackupArchive(
    val metadata: Backup.Metadata,
    val archiveFile: File,
    val encrypted: Boolean,
)

/**
 * Shared archive construction for the interactive backup screen and the
 * background scheduler. The writer owns only app-private paths; SAF
 * publication is deliberately a separate step so a partially written
 * document can never be presented as a completed archive.
 */
internal object BackupArchiveBuilder {
    suspend fun build(
        context: Context,
        inputDir: File,
        outputDir: File,
        selection: Backup.FilesSelection,
        passphrase: CharArray?,
        forceEncryption: Boolean = false,
    ): BuiltBackupArchive = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        val clipboardHistory = if (selection.containsClipboard) {
            appContext.clipboardManager().value.snapshotHistoryForRestore()
                .filterNot { it.isSensitive }
        } else {
            emptyList()
        }

        check(inputDir.deleteRecursively() || !inputDir.exists()) {
            "Could not clear the backup input workspace."
        }
        check(outputDir.deleteRecursively() || !outputDir.exists()) {
            "Could not clear the backup output workspace."
        }
        check(inputDir.mkdirs()) { "Could not create the backup input workspace." }
        check(outputDir.mkdirs()) { "Could not create the backup output workspace." }

        if (selection.jetprefDatastore) {
            val fileBasedStorage = inputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                .let { FileBasedStorage(it.path) }
            FlorisPreferenceStore.export(fileBasedStorage).getOrThrow()
        }
        val workspaceFilesDir = inputDir.subDir("files")
        if (selection.imeKeyboard) {
            appContext.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).let { dir ->
                dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH))
            }
        }
        if (selection.keypressSounds) {
            val keypressSoundsDir = appContext.filesDir.subDir(BackupArchiveStores.KeypressSoundsDirName)
            if (keypressSoundsDir.exists()) {
                BackupArchiveStores.copyDirectory(
                    keypressSoundsDir,
                    workspaceFilesDir.subDir(BackupArchiveStores.KeypressSoundsDirName),
                )
            }
        }
        if (selection.imeTheme) {
            appContext.filesDir.subDir(ExtensionManager.IME_THEME_PATH).let { dir ->
                dir.copyRecursively(workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH))
            }
        }
        if (selection.localStickerPacks) {
            val stickerDir = LocalStickerPackRepository.storageDir(appContext)
            if (stickerDir.exists()) {
                stickerDir.copyRecursively(
                    workspaceFilesDir.subDir(LocalStickerPackRepository.StorageDirName),
                    overwrite = true,
                )
            }
        }
        if (selection.snippets) {
            val snippetsDir = appContext.filesDir.subDir(BackupArchiveStores.SnippetsDirName)
            if (snippetsDir.exists()) {
                BackupArchiveStores.copyDirectory(
                    snippetsDir,
                    workspaceFilesDir.subDir(BackupArchiveStores.SnippetsDirName),
                )
            }
        }
        if (selection.hardwareKeyboardLayouts) {
            val layoutFile = appContext.filesDir.subFile(
                BackupArchiveStores.HardwareKeyboardLayoutFileName,
            )
            if (layoutFile.isFile) {
                BackupArchiveStores.copyFile(
                    layoutFile,
                    workspaceFilesDir.subFile(BackupArchiveStores.HardwareKeyboardLayoutFileName),
                )
            }
        }
        if (selection.customEmojiTags) {
            val tagFile = appContext.filesDir.subFile(BackupArchiveStores.CustomEmojiTagsFileName)
            if (tagFile.isFile) {
                BackupArchiveStores.copyFile(
                    tagFile,
                    workspaceFilesDir.subFile(BackupArchiveStores.CustomEmojiTagsFileName),
                )
            }
        }
        if (selection.emojiPinGroups) {
            val pinGroupFile = appContext.filesDir.subFile(BackupArchiveStores.EmojiPinGroupsFileName)
            if (pinGroupFile.isFile) {
                BackupArchiveStores.copyFile(
                    pinGroupFile,
                    workspaceFilesDir.subFile(BackupArchiveStores.EmojiPinGroupsFileName),
                )
            }
        }

        if (selection.containsClipboard) {
            val clipboardFilesDir = inputDir.subDir("clipboard")
            clipboardFilesDir.mkdir()
            if (selection.clipboardTextItems) {
                clipboardFilesDir.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME)
                    .writeJson(clipboardHistory.filter { it.type == ItemType.TEXT })
            }
            if (selection.clipboardImageItems) {
                clipboardFilesDir.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME)
                    .writeJson(clipboardHistory.filter { it.type == ItemType.IMAGE })
                for (item in clipboardHistory.filter { it.type == ItemType.IMAGE }) {
                    val uri = item.uri ?: continue
                    val id = ContentUris.parseId(uri)
                    ClipboardFileStorage.copyDecryptedTo(
                        context = appContext,
                        id = id,
                        target = clipboardFilesDir.subFile(
                            "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id",
                        ),
                        mediaKind = ClipboardFileStorage.MediaKind.IMAGE,
                    )
                }
            }
            if (selection.clipboardVideoItems) {
                clipboardFilesDir.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME)
                    .writeJson(clipboardHistory.filter { it.type == ItemType.VIDEO })
                for (item in clipboardHistory.filter { it.type == ItemType.VIDEO }) {
                    val uri = item.uri ?: continue
                    val id = ContentUris.parseId(uri)
                    ClipboardFileStorage.copyDecryptedTo(
                        context = appContext,
                        id = id,
                        target = clipboardFilesDir.subFile(
                            "${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$id",
                        ),
                        mediaKind = ClipboardFileStorage.MediaKind.VIDEO,
                    )
                }
            }
        }

        val metadata = Backup.Metadata(
            packageName = dev.patrickgold.florisboard.BuildConfig.APPLICATION_ID,
            versionCode = dev.patrickgold.florisboard.BuildConfig.VERSION_CODE,
            versionName = dev.patrickgold.florisboard.BuildConfig.VERSION_NAME,
            timestamp = System.currentTimeMillis(),
            archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
        )
        inputDir.subFile(Backup.METADATA_JSON_NAME).writeJson(metadata)
        val plaintextZip = outputDir.subFile(Backup.defaultFileName(metadata))
        ZipUtils.zip(inputDir, plaintextZip)

        val encrypted = forceEncryption || selection.containsClipboard
        if (!encrypted) {
            return@withContext BuiltBackupArchive(metadata, plaintextZip, encrypted = false)
        }

        val secret = requireNotNull(passphrase) {
            "Encrypted backups require a passphrase."
        }
        require(secret.isNotEmpty()) { "Encrypted backups require a non-empty passphrase." }
        val encryptedArchive = outputDir.subFile(
            Backup.defaultFileName(metadata, encrypted = true),
        )
        PortableBackupEnvelope.encrypt(
            plaintextZip = plaintextZip,
            encryptedTarget = encryptedArchive,
            passphrase = secret,
            containsClipboard = selection.containsClipboard,
        )
        check(plaintextZip.delete()) {
            "Could not remove the app-private plaintext backup ZIP."
        }
        check(inputDir.deleteRecursively()) {
            "Could not remove the app-private plaintext backup workspace."
        }
        BuiltBackupArchive(metadata, encryptedArchive, encrypted = true)
    }
}
