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

package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.copyToLimited

data class VoiceModelInstallState(
    val modelId: String,
    val installed: Boolean,
    val diskBytes: Long,
    val artifactName: String?,
)

class VoiceModelInstallStore(
    private val rootDir: File,
) {
    @Synchronized
    fun state(entry: VoiceModelCatalogEntry): VoiceModelInstallState {
        val dir = entry.modelDir()
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val bytes = files.sumOf { it.length().coerceAtLeast(0L) }
        return VoiceModelInstallState(
            modelId = entry.id,
            installed = bytes > 0L,
            diskBytes = bytes,
            artifactName = files.maxByOrNull { it.length() }?.name,
        )
    }

    @Synchronized
    fun states(entries: List<VoiceModelCatalogEntry>): Map<String, VoiceModelInstallState> {
        return entries.associate { entry -> entry.id to state(entry) }
    }

    @Synchronized
    fun install(
        entry: VoiceModelCatalogEntry,
        displayName: String?,
        inputStream: InputStream,
    ): VoiceModelInstallState {
        rootDir.mkdirs()
        sweepStaleStagingDirs()
        val dir = entry.modelDir()
        val stagingDir = File(rootDir, "$STAGING_PREFIX${entry.id}-${UUID.randomUUID()}").canonicalFile
        stagingDir.deleteRecursively()
        check(stagingDir.mkdirs()) { "Unable to create temporary model install directory." }
        val targetName = sanitizeArtifactName(displayName, entry.artifactFileName)
        val tmpFile = File(stagingDir, "$targetName.tmp")
        val targetFile = File(stagingDir, targetName)
        try {
            val maxArtifactBytes = maxArtifactBytes(entry)
            inputStream.use { input ->
                copyModelArtifact(input, tmpFile, maxArtifactBytes)
            }
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
            activateStagedInstall(dir, stagingDir)
            return state(entry)
        } catch (cause: Throwable) {
            stagingDir.deleteRecursively()
            throw cause
        }
    }

    /** Best-effort cleanup of staging/backup directories left behind by a
     *  prior crashed install. Names are anchored to our own prefixes so we
     *  never sweep a real model directory even if a future model id were
     *  introduced that incidentally collides with the prefix. */
    private fun sweepStaleStagingDirs() {
        val children = rootDir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            val name = child.name
            if (name.startsWith(STAGING_PREFIX) || name.startsWith(BACKUP_PREFIX)) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    @Synchronized
    fun delete(entry: VoiceModelCatalogEntry): Boolean {
        return entry.modelDir().deleteRecursively()
    }

    private fun VoiceModelCatalogEntry.modelDir(): File {
        require(SafeModelIdPattern.matches(id)) { "Invalid voice model id '$id'." }
        val canonicalRoot = rootDir.canonicalFile
        val dir = File(canonicalRoot, id).canonicalFile
        check(dir.toPath().startsWith(canonicalRoot.toPath())) {
            "Voice model directory escaped the install root."
        }
        return dir
    }

    private fun activateStagedInstall(targetDir: File, stagingDir: File) {
        val backupDir = File(rootDir, "$BACKUP_PREFIX${targetDir.name}-${UUID.randomUUID()}").canonicalFile
        backupDir.deleteRecursively()
        try {
            if (targetDir.exists() && !targetDir.renameTo(backupDir)) {
                error("Unable to move existing voice model aside for replacement.")
            }
            if (!stagingDir.renameTo(targetDir)) {
                restorePreviousInstall(targetDir, backupDir)
                error("Unable to activate voice model artifact.")
            }
            backupDir.deleteRecursively()
        } catch (cause: Throwable) {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            throw cause
        }
    }

    private fun restorePreviousInstall(targetDir: File, backupDir: File) {
        if (!backupDir.exists()) return
        targetDir.deleteRecursively()
        if (!backupDir.renameTo(targetDir)) {
            backupDir.copyRecursively(targetDir, overwrite = true)
            backupDir.deleteRecursively()
        }
    }

    companion object {
        private val SafeModelIdPattern = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")
        private val UnsafeArtifactNameChars = Regex("""[\p{Cntrl}/\\:*?"<>|]+""")
        private const val ArtifactSizeHeadroomMb = 32
        // Dotted prefixes keep these out of `state()`'s `listFiles()` filter
        // (it skips non-files) but more importantly they make the leftover
        // dirs unambiguously ours so the sweeper never touches a real
        // model directory. `SafeModelIdPattern` requires the first char
        // to be alphanumeric so genuine model ids cannot collide.
        private const val STAGING_PREFIX = ".swiftfloris-staging-"
        private const val BACKUP_PREFIX = ".swiftfloris-backup-"

        internal fun sanitizeArtifactName(displayName: String?, fallbackName: String): String {
            val sanitized = displayName
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(UnsafeArtifactNameChars, "_")
                ?.trim()
                ?.trim('.')
                ?.take(160)
                ?.takeIf { it.isNotBlank() }
            return sanitized ?: fallbackName
        }

        internal fun maxArtifactBytes(entry: VoiceModelCatalogEntry): Long {
            val headroomMb = maxOf(ArtifactSizeHeadroomMb, entry.approximateSizeMb / 4)
            return (entry.approximateSizeMb.toLong() + headroomMb.toLong()) * 1024L * 1024L
        }

        internal fun requireArtifactSize(entry: VoiceModelCatalogEntry, byteCount: Long) {
            if (byteCount < 0L) return
            val maxArtifactBytes = maxArtifactBytes(entry)
            check(byteCount <= maxArtifactBytes) {
                "Selected voice model artifact is $byteCount bytes; maximum for ${entry.id} is $maxArtifactBytes bytes."
            }
        }

        internal fun copyModelArtifact(inputStream: InputStream, targetFile: File, maxBytes: Long): Long {
            targetFile.outputStream().use { output ->
                return inputStream.copyToLimited(output, maxBytes)
            }
        }
    }
}

class VoiceModelInstallRepository(
    private val context: Context,
    private val store: VoiceModelInstallStore = VoiceModelInstallStore(File(context.filesDir, VoiceModelsDirName)),
) {
    suspend fun states(entries: List<VoiceModelCatalogEntry>): Map<String, VoiceModelInstallState> {
        return withContext(Dispatchers.IO) {
            store.states(entries)
        }
    }

    suspend fun installFromUri(
        entry: VoiceModelCatalogEntry,
        uri: Uri,
    ): VoiceModelInstallState {
        return withContext(Dispatchers.IO) {
            val displayName = context.displayName(uri)
            context.fileSize(uri)?.let { reportedSize ->
                VoiceModelInstallStore.requireArtifactSize(entry, reportedSize)
            }
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open selected model artifact.")
            store.install(
                entry = entry,
                displayName = displayName,
                inputStream = inputStream,
            )
        }
    }

    suspend fun delete(entry: VoiceModelCatalogEntry): Boolean {
        return withContext(Dispatchers.IO) {
            store.delete(entry)
        }
    }

    private fun Context.displayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
            } else {
                null
            }
        } ?: uri.lastPathSegment
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Context.fileSize(uri: Uri): Long? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLongOrNull(OpenableColumns.SIZE)
            } else {
                null
            }
        }
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    companion object {
        private const val VoiceModelsDirName = "voice-models"
    }
}
