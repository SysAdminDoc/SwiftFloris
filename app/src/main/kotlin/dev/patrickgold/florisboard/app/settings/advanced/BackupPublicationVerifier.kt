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
import androidx.annotation.StringRes
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaEncryption
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.media.sticker.LocalStickerPackRepository
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.florisboard.lib.util.summarizeForUser
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile

/** What a checked backup archive holds, shown once its published copy matches it. */
internal data class BackupReceipt(
    val archiveVersion: Int,
    val encrypted: Boolean,
    /** The sections the archive carries, found by the paths restore reads them from. */
    val sections: Backup.FilesSelection,
    val byteSize: Long,
    /** Lowercase hex SHA-256 of the archive bytes. */
    val sha256: String,
) : java.io.Serializable

internal class BackupVerificationException(
    val problem: Problem,
    cause: Throwable? = null,
) : RuntimeException("Backup verification failed: $problem", cause) {
    /**
     * [message] advises someone saving a backup by hand. [scheduledMessage] advises the owner of
     * a scheduled backup, which picks its own sections and passphrase and runs again on its own.
     */
    enum class Problem(@StringRes val message: Int, @StringRes val scheduledMessage: Int) {
        /** The destination could not be opened or read back. */
        ReadbackFailed(
            R.string.backup_and_restore__verify__readback_failed,
            R.string.scheduled_backup__verify__readback_failed,
        ),
        /** The destination holds fewer bytes than the archive. */
        Truncated(R.string.backup_and_restore__verify__truncated, R.string.scheduled_backup__verify__truncated),
        /** The destination holds different bytes, or more of them. */
        Altered(R.string.backup_and_restore__verify__altered, R.string.scheduled_backup__verify__altered),
        /** The archive is past the size restore accepts. */
        TooLarge(R.string.backup_and_restore__verify__too_large, R.string.scheduled_backup__verify__too_large),
        /** The passphrase does not open the archive, or its ciphertext was changed. */
        WrongPassphrase(
            R.string.backup_and_restore__verify__wrong_passphrase,
            R.string.scheduled_backup__verify__wrong_passphrase,
        ),
        /** The archive, or a section restore parses, can't be read. */
        Damaged(R.string.backup_and_restore__verify__damaged, R.string.scheduled_backup__verify__damaged),
        /** The archive parses, but restore would refuse it. */
        Invalid(R.string.backup_and_restore__verify__invalid, R.string.scheduled_backup__verify__invalid),
        /** The phone ran out of space for the copy the check unpacks. */
        NoSpace(R.string.backup_and_restore__verify__no_space, R.string.scheduled_backup__verify__no_space),
    }
}

/** Whether [this] or one of its causes is a write that found the storage full. */
internal fun Throwable.isOutOfSpace(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { error ->
        error.message?.let { "ENOSPC" in it || "No space left" in it } == true
    }

/**
 * One check for every backup this app publishes, interactive or scheduled.
 *
 * [inspect] opens a finished archive the way restore does: the same size limit, the envelope
 * authenticated and decrypted, the ZIP unpacked under restore's limits, its metadata and
 * sections validated by restore's own rules, and the sections restore parses (settings and
 * clipboard lists) parsed the same way. [verifyReadback] then reads the published copy back and
 * requires the same byte count and SHA-256, so the copy is the archive [inspect] authenticated,
 * without asking for the passphrase a second time.
 */
internal object BackupPublicationVerifier {
    private const val BufferBytes = 64 * 1024

    suspend fun inspect(
        archive: File,
        passphrase: CharArray?,
        scratchDir: File,
        maxBytes: Long = Restore.MAX_PORTABLE_ARCHIVE_BYTES,
    ): BackupReceipt {
        val byteSize = archive.length()
        if (byteSize > maxBytes) fail(BackupVerificationException.Problem.TooLarge)
        val sha256 = try {
            FileInputStream(archive).use { input ->
                sha256(input, maxBytes).also { (_, read) ->
                    if (read > maxBytes) fail(BackupVerificationException.Problem.TooLarge)
                    if (read != byteSize) fail(BackupVerificationException.Problem.Damaged)
                }.first
            }
        } catch (error: BackupVerificationException) {
            throw error
        } catch (error: Throwable) {
            fail(BackupVerificationException.Problem.Damaged, error)
        }
        val encrypted = PortableBackupEnvelope.isEncryptedEnvelope(archive)
        val root = scratchDir.subDir("verify-${System.nanoTime()}")
        val receipt = try {
            inspectUnpacked(archive, passphrase, encrypted, root).let { (archiveVersion, sections) ->
                BackupReceipt(
                    archiveVersion = archiveVersion,
                    encrypted = encrypted,
                    sections = sections,
                    byteSize = byteSize,
                    sha256 = sha256,
                )
            }
        } catch (error: Throwable) {
            root.deleteRecursively()
            throw error
        }
        // The check decrypted the archive, so a copy left behind would be plaintext on disk.
        if (!root.deleteRecursively()) {
            throw IOException("Couldn't remove the decrypted copy made to check the backup.")
        }
        return receipt
    }

    private suspend fun inspectUnpacked(
        archive: File,
        passphrase: CharArray?,
        encrypted: Boolean,
        root: File,
    ): Pair<Int, Backup.FilesSelection> {
        createDirectory(root)
        val zip = if (encrypted) {
            if (passphrase == null) fail(BackupVerificationException.Problem.WrongPassphrase)
            root.subFile("authenticated.zip").also { plaintext ->
                try {
                    PortableBackupEnvelope.decrypt(archive, plaintext, passphrase)
                } catch (error: PortableBackupEnvelopeException) {
                    fail(
                        when (error.reason) {
                            PortableBackupEnvelope.FailureReason.BadPassphraseOrTampered ->
                                BackupVerificationException.Problem.WrongPassphrase
                            PortableBackupEnvelope.FailureReason.Oversized ->
                                BackupVerificationException.Problem.TooLarge
                            // Nothing has been saved yet, so a short envelope here is a
                            // build that went wrong, not a storage app that stopped early.
                            else -> BackupVerificationException.Problem.Damaged
                        },
                        error,
                    )
                } catch (error: Throwable) {
                    fail(damagedUnlessOutOfSpace(error), error)
                }
            }
        } else {
            archive
        }
        val contents = root.subDir("contents").also(::createDirectory)
        try {
            ZipUtils.unzip(zip, contents)
        } catch (error: Throwable) {
            fail(damagedUnlessOutOfSpace(error), error)
        }
        requireFullyUnpacked(zip, contents)
        val metadata: Backup.Metadata = try {
            contents.subFile(Backup.METADATA_JSON_NAME).readJson()
        } catch (error: FileNotFoundException) {
            fail(BackupVerificationException.Problem.Invalid, error)
        } catch (error: Throwable) {
            fail(BackupVerificationException.Problem.Damaged, error)
        }
        val sections = BackupArchiveContents.sectionsIn(contents)
        val validation = BackupRestorePolicy.validateRestoreArchive(
            metadata = metadata,
            currentVersionCode = BuildConfig.VERSION_CODE,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = BackupArchiveContents.hasRestorableContent(sections),
        )
        if (validation.errorId != null) fail(BackupVerificationException.Problem.Invalid)
        try {
            BackupArchiveContents.requireClipboardRestorable(contents, sections)
        } catch (error: Throwable) {
            fail(BackupVerificationException.Problem.Damaged, error)
        }
        if (sections.jetprefDatastore) {
            try {
                BackupArchiveContents.requireSettingsImport(contents)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(BackupVerificationException.Problem.Damaged, error)
            }
        }
        return metadata.archiveVersion to sections
    }

    /**
     * Unzip leaves out a file past its size limits, or with a name too long for the phone,
     * without failing, and restore unpacks the same way. So every file the ZIP lists has to be
     * there in full, or restore would quietly bring back less than the backup holds.
     */
    private fun requireFullyUnpacked(zip: File, contents: File) {
        var listedBytes = 0L
        val left = try {
            ZipFile(zip).use { archive ->
                archive.entries().asSequence().filterNot { it.isDirectory }.firstOrNull { entry ->
                    listedBytes += entry.size.coerceAtLeast(0L)
                    val file = File(contents, entry.name)
                    !file.isFile || (entry.size >= 0L && file.length() != entry.size)
                }
            }
        } catch (error: IOException) {
            fail(damagedUnlessOutOfSpace(error), error)
        } ?: return
        fail(missingFileProblem(left.size, listedBytes))
    }

    /**
     * Why unzip left out a file of [entrySize] bytes when the files listed up to and including it
     * add up to [listedBytes]. Mirrors ZipUtils: a file past the per-file limit is skipped, and so
     * is every file once the ones before it used up the archive limit, an empty one included.
     */
    internal fun missingFileProblem(entrySize: Long, listedBytes: Long): BackupVerificationException.Problem {
        val unpackedBefore = listedBytes - entrySize.coerceAtLeast(0L)
        val pastLimits = entrySize > ZipUtils.MaxUnzippedEntrySizeBytes ||
            listedBytes > ZipUtils.MaxUnzippedArchiveSizeBytes ||
            unpackedBefore >= ZipUtils.MaxUnzippedArchiveSizeBytes
        return if (pastLimits) BackupVerificationException.Problem.TooLarge else BackupVerificationException.Problem.Damaged
    }

    /** The check's private folders. Unlike mkdirs(), a full phone reads as one here. */
    private fun createDirectory(dir: File) {
        try {
            Files.createDirectories(dir.toPath())
        } catch (error: IOException) {
            if (error.isOutOfSpace()) fail(BackupVerificationException.Problem.NoSpace, error)
            throw error
        }
    }

    /** Reads the published copy through [open] and requires it to be the archive in [receipt]. */
    fun verifyReadback(receipt: BackupReceipt, open: () -> InputStream?) {
        val (digest, read) = try {
            val input = open() ?: throw FileNotFoundException("The destination returned no stream.")
            // One byte past the archive is enough to tell a longer copy from a matching one.
            input.use { sha256(it, receipt.byteSize + 1) }
        } catch (error: Throwable) {
            fail(BackupVerificationException.Problem.ReadbackFailed, error)
        }
        when {
            read < receipt.byteSize -> fail(BackupVerificationException.Problem.Truncated)
            read > receipt.byteSize || digest != receipt.sha256 ->
                fail(BackupVerificationException.Problem.Altered)
        }
    }

    /** Hex SHA-256 and byte count of [input], stopping once more than [limit] bytes arrive. */
    private fun sha256(input: InputStream, limit: Long): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BufferBytes)
        var total = 0L
        while (total <= limit) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) } to total
    }

    /** The decrypted and unpacked copies need room of their own, so a full phone isn't a bad archive. */
    private fun damagedUnlessOutOfSpace(error: Throwable): BackupVerificationException.Problem =
        if (error.isOutOfSpace()) {
            BackupVerificationException.Problem.NoSpace
        } else {
            BackupVerificationException.Problem.Damaged
        }

    private fun fail(problem: BackupVerificationException.Problem, cause: Throwable? = null): Nothing =
        throw BackupVerificationException(problem, cause)
}

/** Which sections an unpacked archive carries, by the same paths restore reads them from. */
internal object BackupArchiveContents {
    fun sectionsIn(contents: File): Backup.FilesSelection {
        val files = contents.subDir("files")
        val clipboard = contents.subDir("clipboard")
        return Backup.FilesSelection(
            jetprefDatastore = settingsFile(contents).exists(),
            imeKeyboard = files.subDir(ExtensionManager.IME_KEYBOARD_PATH).exists(),
            keypressSounds = files.subDir(BackupArchiveStores.KeypressSoundsDirName).exists(),
            imeTheme = files.subDir(ExtensionManager.IME_THEME_PATH).exists(),
            localStickerPacks = files.subDir(LocalStickerPackRepository.StorageDirName).exists(),
            snippets = files.subDir(BackupArchiveStores.SnippetsDirName).exists(),
            hardwareKeyboardLayouts = files.subFile(BackupArchiveStores.HardwareKeyboardLayoutFileName).isFile,
            customEmojiTags = files.subFile(BackupArchiveStores.CustomEmojiTagsFileName).isFile,
            emojiPinGroups = files.subFile(BackupArchiveStores.EmojiPinGroupsFileName).isFile,
            clipboardTextItems = clipboard.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME).exists(),
            clipboardImageItems = clipboard.subFile(Backup.CLIPBOARD_IMAGES_JSON_NAME).exists(),
            clipboardVideoItems = clipboard.subFile(Backup.CLIPBOARD_VIDEO_JSON_NAME).exists(),
        )
    }

    private fun settingsFile(contents: File): File = contents
        .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
        .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")

    /**
     * Imports the archived settings the way restore does, into a store of its own that nothing
     * reads or saves, so a file restore's import would reject fails here and the live settings
     * stay as they are.
     */
    suspend fun requireSettingsImport(contents: File) {
        val scratchStore = jetprefDataStoreOf(FlorisPreferenceModel::class)
        scratchStore.init(LoadStrategy.Disabled, PersistStrategy.Disabled).getOrThrow()
        scratchStore.import(ImportStrategy.Erase, FileBasedStorage(settingsFile(contents).path)).getOrThrow()
    }

    /**
     * Reads the clipboard lists the way restore does, and checks each image and video entry
     * names a media file the archive carries within restore's size limit. Restore fails the
     * whole section otherwise. Settings go through [requireSettingsImport]. The other sections
     * are copies of store files restore copies back as they are.
     */
    fun requireClipboardRestorable(contents: File, sections: Backup.FilesSelection) {
        val clipboard = contents.subDir("clipboard")
        if (sections.clipboardTextItems) {
            clipboard.subFile(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME).readJson<List<ClipboardItem>>()
        }
        val mediaLists = listOf(
            Triple(sections.clipboardImageItems, Backup.CLIPBOARD_IMAGES_JSON_NAME, ClipboardFileStorage.MediaKind.IMAGE),
            Triple(sections.clipboardVideoItems, Backup.CLIPBOARD_VIDEO_JSON_NAME, ClipboardFileStorage.MediaKind.VIDEO),
        )
        for ((present, listName, mediaKind) in mediaLists) {
            if (!present) continue
            val itemType = when (mediaKind) {
                ClipboardFileStorage.MediaKind.IMAGE -> ItemType.IMAGE
                ClipboardFileStorage.MediaKind.VIDEO -> ItemType.VIDEO
            }
            clipboard.subFile(listName).readJson<List<ClipboardItem>>()
                .filter { it.type == itemType }
                .forEach { item ->
                    val archivedFileId = checkNotNull(item.uri?.lastPathSegment?.toLongOrNull()) {
                        "Clipboard backup contains an invalid provider id."
                    }
                    val media = clipboard.subFile("${ClipboardFileStorage.CLIPBOARD_FILES_PATH}/$archivedFileId")
                    check(media.isFile) { "Clipboard backup media file is missing." }
                    check(ClipboardMediaEncryption.isEncrypted(media) || media.length() <= mediaKind.maxCloneBytes) {
                        "Clipboard backup media file is larger than restore accepts."
                    }
                }
        }
    }

    fun hasRestorableContent(sections: Backup.FilesSelection): Boolean =
        BackupRestorePolicy.hasRestorableContent(
            hasJetprefDatastore = sections.jetprefDatastore,
            hasImeKeyboard = sections.imeKeyboard,
            hasImeTheme = sections.imeTheme,
            hasLocalStickerPacks = sections.localStickerPacks,
            hasClipboardTextItems = sections.clipboardTextItems,
            hasClipboardImageItems = sections.clipboardImageItems,
            hasClipboardVideoItems = sections.clipboardVideoItems,
            hasSnippets = sections.snippets,
            hasHardwareKeyboardLayouts = sections.hardwareKeyboardLayouts,
            hasCustomEmojiTags = sections.customEmojiTags,
            hasEmojiPinGroups = sections.emojiPinGroups,
            hasKeypressSounds = sections.keypressSounds,
        )
}

/** What a manual save whose write or check failed did with the document the user picked. */
internal enum class FailedSaveCleanup(@StringRes val message: Int) {
    /** The document was deleted. It was empty before the save, so nothing of the user's went. */
    Removed(R.string.backup_and_restore__back_up__cleanup_removed),
    /** The provider refused the delete, so the unchecked file is still there. */
    LeftInPlace(R.string.backup_and_restore__back_up__cleanup_left_in_place),
}

internal class ManualBackupSaveException(
    val cleanup: FailedSaveCleanup,
    cause: Throwable,
) : IOException(cause.message, cause)

/** The picked document was turned down before anything was written to it. */
internal class PickedDocumentRefusedException(
    val reason: Reason,
    cause: Throwable? = null,
) : IOException("Picked document refused: $reason", cause) {
    enum class Reason(@StringRes val message: Int) {
        /** It already holds something, which a save that fails partway would destroy. */
        NotEmpty(R.string.backup_and_restore__back_up__picked_not_empty),
        /** It can't be opened, so a save into it couldn't be checked either. */
        Unreadable(R.string.backup_and_restore__back_up__picked_unreadable),
    }
}

/**
 * Saves a checked archive into the document the user picked in the system file picker.
 *
 * A single picked document can't be written beside the old content and swapped in, and
 * truncating it loses that content the moment the write starts. So only a new or empty document
 * is written. One that holds anything, or can't be read, is refused untouched with a
 * [PickedDocumentRefusedException]. When the write or its check fails, the document is deleted,
 * so a failed save never leaves an unchecked file under a backup's name.
 * [ManualBackupSaveException.cleanup] says whether that worked.
 */
internal object ManualBackupSave {
    fun save(
        archive: File,
        receipt: BackupReceipt,
        open: () -> InputStream?,
        write: (File) -> Unit,
        delete: () -> Boolean,
    ) {
        requireEmpty(open)
        try {
            write(archive)
            BackupPublicationVerifier.verifyReadback(receipt, open)
        } catch (error: Throwable) {
            val cleanup = if (runCatching(delete).getOrDefault(false)) {
                FailedSaveCleanup.Removed
            } else {
                FailedSaveCleanup.LeftInPlace
            }
            throw ManualBackupSaveException(cleanup, error)
        }
    }

    private fun requireEmpty(open: () -> InputStream?) {
        val firstByte = try {
            val input = open() ?: throw FileNotFoundException("The picked document returned no stream.")
            input.use { it.read() }
        } catch (error: Throwable) {
            throw PickedDocumentRefusedException(PickedDocumentRefusedException.Reason.Unreadable, error)
        }
        if (firstByte >= 0) throw PickedDocumentRefusedException(PickedDocumentRefusedException.Reason.NotEmpty)
    }
}

/**
 * The message to show for a failed backup: a check's own advice (worded for a [scheduled] backup
 * when it is one), why a picked document was refused, what a failed manual save left behind, or
 * the bounded summary [summarizeForUser] gives anything else.
 */
internal fun Throwable.backupFailureMessage(
    context: Context,
    fallback: String,
    scheduled: Boolean = false,
): String = when (this) {
    is BackupVerificationException -> {
        flogError { stackTraceToString() }
        context.getString(if (scheduled) problem.scheduledMessage else problem.message)
    }
    is PickedDocumentRefusedException -> {
        flogError { stackTraceToString() }
        context.getString(reason.message)
    }
    is ManualBackupSaveException -> {
        val reason = cause?.backupFailureMessage(context, fallback, scheduled) ?: summarizeForUser(fallback)
        "$reason ${context.getString(cleanup.message)}"
    }
    else -> {
        flogError { stackTraceToString() }
        summarizeForUser(fallback)
    }
}

/** Whether [backupFailureMessage] gives this failure advice of its own, not only a summary. */
internal fun Throwable.hasOwnBackupAdvice(): Boolean =
    this is BackupVerificationException || this is ManualBackupSaveException || this is PickedDocumentRefusedException

/**
 * Whether the archive behind this failed manual save is still a checked one worth saving again.
 * Only a failed or refused save qualifies: the archive passed its check before the save started.
 */
internal fun Throwable.leavesArchiveToSaveAgain(): Boolean =
    this is ManualBackupSaveException || this is PickedDocumentRefusedException

/**
 * Whether [archive] is still the file this receipt describes, going by its size. Saving it again
 * reads the copy back against the checksum, so this only turns away an archive that's gone or
 * cut short before a save that would fail anyway.
 */
internal fun BackupReceipt.stillDescribes(archive: File): Boolean = archive.isFile && archive.length() == byteSize

/**
 * Where a manual save leaves its outcome. A save outlives the screen that started it, since a
 * write that started can't be called back, and turning the phone recreates the screen in the
 * middle of one. So the save reports here, by its workspace, and whichever Back up screen holds
 * that workspace shows it: the one that started the save or the one that replaced it.
 */
internal object ManualBackupSaveOutcomes {
    sealed interface Outcome {
        data class Saved(val receipt: BackupReceipt, val selection: Backup.FilesSelection?) : Outcome

        /** [keptToSaveAgain]: the checked archive is still there for Back up to save again. */
        data class Failed(val message: String, val hasOwnAdvice: Boolean, val keptToSaveAgain: Boolean) : Outcome
    }

    private val outcomes = MutableStateFlow<Map<String, Outcome>>(emptyMap())
    private val holders = mutableMapOf<String, Int>()

    /** Workspaces whose screens left for good while a save was still running. Guarded by holders. */
    private val abandoned = mutableSetOf<String>()

    /** Holds passed on by a turning screen, waiting for the screen that replaces it. */
    private val handedOver = mutableSetOf<String>()

    /**
     * A screen showing [workspaceUuid] marks it held until [release], so its archive can be kept.
     * A screen recreated by a turn takes over the hold its predecessor [handOver]ed.
     */
    fun hold(workspaceUuid: String) = synchronized(holders) {
        abandoned.remove(workspaceUuid)
        if (handedOver.remove(workspaceUuid)) return@synchronized
        holders[workspaceUuid] = (holders[workspaceUuid] ?: 0) + 1
    }

    /**
     * The screen holding [workspaceUuid] is going because the phone turned. The hold stays until
     * the recreated screen takes it over in [hold], since the old screen goes before the new one
     * composes and a save finishing in between would otherwise find nobody holding it.
     */
    fun handOver(workspaceUuid: String) = synchronized(holders) {
        if (workspaceUuid in holders) handedOver += workspaceUuid
    }

    fun release(workspaceUuid: String) = synchronized(holders) {
        val count = (holders[workspaceUuid] ?: 0) - 1
        if (count > 0) holders[workspaceUuid] = count else holders.remove(workspaceUuid)
    }

    fun isHeld(workspaceUuid: String): Boolean = synchronized(holders) { workspaceUuid in holders }

    /**
     * The screen holding [workspaceUuid] is going. [turning]: the phone turned and the screen comes
     * back. [stillShown]: the workspace is still the one it shows, not one it moved on from.
     *
     * Turning hands the hold over instead of letting it lapse, since the old screen goes before
     * the recreated one holds the workspace and a save failing in between should keep its archive.
     * Leaving for good releases it and [abandon]s it, returning the outcome that dropped.
     */
    fun screenGone(workspaceUuid: String, turning: Boolean, stillShown: Boolean, saveRunning: Boolean): Outcome? {
        if (turning && stillShown) {
            handOver(workspaceUuid)
            return null
        }
        release(workspaceUuid)
        if (turning || !stillShown) return null
        return abandon(workspaceUuid, saveRunning)
    }

    /**
     * The last screen showing [workspaceUuid] left for good, not to turn. Unless another screen
     * still holds it, drops and returns an outcome nobody took, and when [saveRunning] and nothing
     * was waiting, drops the outcome that save reports later. The caller closes an archive the
     * dropped outcome kept, since no screen will offer it again.
     */
    fun abandon(workspaceUuid: String, saveRunning: Boolean): Outcome? = synchronized(holders) {
        if (workspaceUuid in holders) return null
        var dropped: Outcome? = null
        outcomes.update { current ->
            dropped = current[workspaceUuid]
            current - workspaceUuid
        }
        if (dropped == null && saveRunning) abandoned += workspaceUuid
        dropped
    }

    fun report(workspaceUuid: String, outcome: Outcome) {
        synchronized(holders) {
            if (abandoned.remove(workspaceUuid)) return
            outcomes.update { it + (workspaceUuid to outcome) }
        }
    }

    /** Waits for [workspaceUuid]'s outcome, which may already be here, and removes it. */
    suspend fun take(workspaceUuid: String): Outcome {
        val outcome = outcomes.mapNotNull { it[workspaceUuid] }.first()
        outcomes.update { it - workspaceUuid }
        return outcome
    }
}
