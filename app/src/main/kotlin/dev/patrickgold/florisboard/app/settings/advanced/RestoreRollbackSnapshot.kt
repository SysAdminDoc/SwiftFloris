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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.media.emoji.CustomEmojiTagStore
import dev.patrickgold.florisboard.ime.media.emoji.EmojiPinGroupStore
import dev.patrickgold.florisboard.ime.media.sticker.LocalStickerPackRepository
import dev.patrickgold.florisboard.ime.media.sticker.evictStickerBitmapCache
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

/**
 * App-private recovery snapshot for one restore attempt.
 *
 * Capture completes before any live store is mutated. If any selected section
 * fails (or the restore coroutine is cancelled), [restore] puts every selected
 * live store back from this snapshot before the workspace is deleted.
 */
internal class RestoreRollbackSnapshot private constructor(
    private val context: Context,
    private val workspace: CacheManager.BackupAndRestoreWorkspace,
    private val selection: Backup.FilesSelection,
    private val keyboardDirExisted: Boolean,
    private val themeDirExisted: Boolean,
    private val stickerDirExisted: Boolean,
    private val snippetsDirExisted: Boolean,
    private val hardwareKeyboardLayoutsFileExisted: Boolean,
    private val customEmojiTagsFileExisted: Boolean,
    private val emojiPinGroupsFileExisted: Boolean,
) : Closeable {
    companion object {
        private const val SnapshotRootName = "restore-rollback"
        private const val PreferencesDirName = "preferences"
        private const val KeyboardDirName = "keyboard"
        private const val ThemeDirName = "theme"
        private const val StickerDirName = "stickers"
        private const val SnippetsDirName = "snippets"
        private const val HardwareKeyboardLayoutsFileName = "hardware-keyboard-layouts.json"
        private const val CustomEmojiTagsFileName = "custom-emoji-tags.json"
        private const val EmojiPinGroupsFileName = "emoji-pin-groups.json"
        private const val ClipboardDirName = "clipboard"
        private const val ClipboardHistoryName = "history.json"
        private const val ClipboardFileInfoName = "file-info.json"
        private const val ClipboardMediaDirName = "media"

        suspend fun capture(
            context: Context,
            cacheManager: CacheManager,
            selection: Backup.FilesSelection,
        ): RestoreRollbackSnapshot {
            val workspace = cacheManager.backupAndRestore.new()
            return try {
                val directoryPresence = withContext(Dispatchers.IO) {
                    val snapshotRoot = workspace.inputDir.subDir(SnapshotRootName)
                    snapshotRoot.mkdirs()

                    if (selection.jetprefDatastore) {
                        val preferenceFile = snapshotRoot
                            .subDir(PreferencesDirName)
                            .subFile(
                                "${FlorisPreferenceModel.NAME}." +
                                    AndroidAppDataStorage.JETPREF_FILE_EXT,
                            )
                        FlorisPreferenceStore.export(
                            FileBasedStorage(preferenceFile.path),
                        ).getOrThrow()
                    }

                    val keyboardExisted = captureDirectory(
                        selected = selection.imeKeyboard,
                        source = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH),
                        target = snapshotRoot.subDir(KeyboardDirName),
                    )
                    val themeExisted = captureDirectory(
                        selected = selection.imeTheme,
                        source = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH),
                        target = snapshotRoot.subDir(ThemeDirName),
                    )
                    val stickerExisted = captureDirectory(
                        selected = selection.localStickerPacks,
                        source = LocalStickerPackRepository.storageDir(context),
                        target = snapshotRoot.subDir(StickerDirName),
                    )
                    val snippetsExisted = captureDirectory(
                        selected = selection.snippets,
                        source = context.filesDir.subDir(BackupArchiveStores.SnippetsDirName),
                        target = snapshotRoot.subDir(SnippetsDirName),
                    )
                    val hardwareKeyboardLayoutsFileExisted = captureFile(
                        selected = selection.hardwareKeyboardLayouts,
                        source = context.filesDir.subFile(
                            BackupArchiveStores.HardwareKeyboardLayoutFileName,
                        ),
                        target = snapshotRoot.subFile(HardwareKeyboardLayoutsFileName),
                    )
                    val customEmojiTagsFileExisted = captureFile(
                        selected = selection.customEmojiTags,
                        source = context.filesDir.subFile(
                            BackupArchiveStores.CustomEmojiTagsFileName,
                        ),
                        target = snapshotRoot.subFile(CustomEmojiTagsFileName),
                    )
                    val emojiPinGroupsFileExisted = captureFile(
                        selected = selection.emojiPinGroups,
                        source = context.filesDir.subFile(
                            BackupArchiveStores.EmojiPinGroupsFileName,
                        ),
                        target = snapshotRoot.subFile(EmojiPinGroupsFileName),
                    )

                    if (selection.containsClipboard) {
                        val clipboardDir = snapshotRoot.subDir(ClipboardDirName)
                        clipboardDir.mkdirs()
                        context.clipboardManager().value
                            .snapshotHistoryForRestore()
                            .let {
                                clipboardDir.subFile(ClipboardHistoryName).writeJson(it)
                            }
                        val filesDb = ClipboardFilesDatabase.new(context)
                        try {
                            clipboardDir.subFile(ClipboardFileInfoName)
                                .writeJson(filesDb.clipboardFilesDao().getAll())
                        } finally {
                            filesDb.close()
                        }
                        val mediaDir = clipboardDir.subDir(ClipboardMediaDirName)
                        mediaDir.mkdirs()
                        ClipboardFileStorage.listStoredFileIds(context).forEach { id ->
                            val source = ClipboardFileStorage.getFileForId(context, id)
                            if (source.isFile) {
                                source.copyTo(mediaDir.subFile(id.toString()), overwrite = true)
                            }
                        }
                    }

                    CapturedStorePresence(
                        keyboardDirExisted = keyboardExisted,
                        themeDirExisted = themeExisted,
                        stickerDirExisted = stickerExisted,
                        snippetsDirExisted = snippetsExisted,
                        hardwareKeyboardLayoutsFileExisted = hardwareKeyboardLayoutsFileExisted,
                        customEmojiTagsFileExisted = customEmojiTagsFileExisted,
                        emojiPinGroupsFileExisted = emojiPinGroupsFileExisted,
                    )
                }
                RestoreRollbackSnapshot(
                    context = context.applicationContext,
                    workspace = workspace,
                    selection = selection,
                    keyboardDirExisted = directoryPresence.keyboardDirExisted,
                    themeDirExisted = directoryPresence.themeDirExisted,
                    stickerDirExisted = directoryPresence.stickerDirExisted,
                    snippetsDirExisted = directoryPresence.snippetsDirExisted,
                    hardwareKeyboardLayoutsFileExisted =
                        directoryPresence.hardwareKeyboardLayoutsFileExisted,
                    customEmojiTagsFileExisted = directoryPresence.customEmojiTagsFileExisted,
                    emojiPinGroupsFileExisted = directoryPresence.emojiPinGroupsFileExisted,
                )
            } catch (error: Throwable) {
                workspace.close()
                throw error
            }
        }

        private fun captureDirectory(
            selected: Boolean,
            source: FsDir,
            target: FsDir,
        ): Boolean {
            if (!selected || !source.exists()) return false
            source.copyRecursively(target, overwrite = true)
            return true
        }

        private fun captureFile(
            selected: Boolean,
            source: org.florisboard.lib.kotlin.io.FsFile,
            target: org.florisboard.lib.kotlin.io.FsFile,
        ): Boolean {
            if (!selected || !source.isFile) return false
            source.copyTo(target, overwrite = true)
            return true
        }

        private data class CapturedStorePresence(
            val keyboardDirExisted: Boolean,
            val themeDirExisted: Boolean,
            val stickerDirExisted: Boolean,
            val snippetsDirExisted: Boolean,
            val hardwareKeyboardLayoutsFileExisted: Boolean,
            val customEmojiTagsFileExisted: Boolean,
            val emojiPinGroupsFileExisted: Boolean,
        )
    }

    private val snapshotRoot: FsDir
        get() = workspace.inputDir.subDir(SnapshotRootName)

    suspend fun restore() = withContext(Dispatchers.IO) {
        val failures = mutableListOf<Throwable>()

        suspend fun restoreStep(block: suspend () -> Unit) {
            runCatching { block() }
                .exceptionOrNull()
                ?.let(failures::add)
        }

        if (selection.jetprefDatastore) {
            restoreStep {
                val preferenceFile = snapshotRoot
                    .subDir(PreferencesDirName)
                    .subFile(
                        "${FlorisPreferenceModel.NAME}." +
                            AndroidAppDataStorage.JETPREF_FILE_EXT,
                    )
                FlorisPreferenceStore.import(
                    ImportStrategy.Erase,
                    FileBasedStorage(preferenceFile.path),
                ).getOrThrow()
            }
        }
        if (selection.imeKeyboard) {
            restoreStep {
                restoreDirectory(
                    snapshot = snapshotRoot.subDir(KeyboardDirName),
                    target = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH),
                    existed = keyboardDirExisted,
                )
            }
        }
        if (selection.imeTheme) {
            restoreStep {
                restoreDirectory(
                    snapshot = snapshotRoot.subDir(ThemeDirName),
                    target = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH),
                    existed = themeDirExisted,
                )
            }
        }
        if (selection.localStickerPacks) {
            restoreStep {
                restoreDirectory(
                    snapshot = snapshotRoot.subDir(StickerDirName),
                    target = LocalStickerPackRepository.storageDir(context),
                    existed = stickerDirExisted,
                )
                evictStickerBitmapCache()
            }
        }
        if (selection.snippets) {
            restoreStep {
                restoreDirectory(
                    snapshot = snapshotRoot.subDir(SnippetsDirName),
                    target = context.filesDir.subDir(BackupArchiveStores.SnippetsDirName),
                    existed = snippetsDirExisted,
                )
                context.snippetManager().value.loadAll()
            }
        }
        if (selection.hardwareKeyboardLayouts) {
            restoreStep {
                restoreFile(
                    snapshot = snapshotRoot.subFile(HardwareKeyboardLayoutsFileName),
                    target = context.filesDir.subFile(
                        BackupArchiveStores.HardwareKeyboardLayoutFileName,
                    ),
                    existed = hardwareKeyboardLayoutsFileExisted,
                )
            }
        }
        if (selection.customEmojiTags) {
            restoreStep {
                restoreFile(
                    snapshot = snapshotRoot.subFile(CustomEmojiTagsFileName),
                    target = context.filesDir.subFile(BackupArchiveStores.CustomEmojiTagsFileName),
                    existed = customEmojiTagsFileExisted,
                )
                CustomEmojiTagStore.get(context).reload()
            }
        }
        if (selection.emojiPinGroups) {
            restoreStep {
                restoreFile(
                    snapshot = snapshotRoot.subFile(EmojiPinGroupsFileName),
                    target = context.filesDir.subFile(BackupArchiveStores.EmojiPinGroupsFileName),
                    existed = emojiPinGroupsFileExisted,
                )
                EmojiPinGroupStore.get(context).reload()
            }
        }
        if (selection.containsClipboard) {
            restoreStep {
                val clipboardDir = snapshotRoot.subDir(ClipboardDirName)
                val history = clipboardDir
                    .subFile(ClipboardHistoryName)
                    .readJson<List<ClipboardItem>>()
                val fileInfos = clipboardDir
                    .subFile(ClipboardFileInfoName)
                    .readJson<List<ClipboardFileInfo>>()

                ClipboardFileStorage.resetClipboardFileStorage(context)
                clipboardDir.subDir(ClipboardMediaDirName)
                    .listFiles()
                    .orEmpty()
                    .filter { it.isFile }
                    .forEach { snapshotFile ->
                        snapshotFile.copyTo(
                            ClipboardFileStorage.getFileForId(
                                context,
                                checkNotNull(snapshotFile.name.toLongOrNull()) {
                                    "Rollback media filename is not a numeric provider id."
                                },
                            ),
                            overwrite = true,
                        )
                    }
                val filesDb = ClipboardFilesDatabase.new(context)
                try {
                    filesDb.clipboardFilesDao().replaceAllForRestore(fileInfos)
                } finally {
                    filesDb.close()
                }
                context.clipboardManager().value.replaceHistoryFromRollback(history)
            }
        }

        if (failures.isNotEmpty()) {
            val rollbackError = IllegalStateException(
                "Restore rollback failed for ${failures.size} selected data store(s).",
            )
            failures.forEach(rollbackError::addSuppressed)
            throw rollbackError
        }
    }

    private fun restoreDirectory(
        snapshot: FsDir,
        target: FsDir,
        existed: Boolean,
    ) {
        check(target.deleteRecursively() || !target.exists()) {
            "Could not clear restore target before rollback."
        }
        if (existed) {
            snapshot.copyRecursively(target, overwrite = true)
        }
    }

    private fun restoreFile(
        snapshot: org.florisboard.lib.kotlin.io.FsFile,
        target: org.florisboard.lib.kotlin.io.FsFile,
        existed: Boolean,
    ) {
        check(target.delete() || !target.exists()) {
            "Could not clear restore target before rollback."
        }
        if (existed) {
            snapshot.copyTo(target, overwrite = true)
        }
    }

    override fun close() {
        workspace.close()
    }
}
