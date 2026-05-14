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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VoiceModelInstallState(
    val modelId: String,
    val installed: Boolean,
    val diskBytes: Long,
    val artifactName: String?,
)

class VoiceModelInstallStore(
    private val rootDir: File,
) {
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

    fun states(entries: List<VoiceModelCatalogEntry>): Map<String, VoiceModelInstallState> {
        return entries.associate { entry -> entry.id to state(entry) }
    }

    fun install(
        entry: VoiceModelCatalogEntry,
        displayName: String?,
        inputStream: InputStream,
    ): VoiceModelInstallState {
        val dir = entry.modelDir()
        dir.deleteRecursively()
        dir.mkdirs()
        val targetName = sanitizeArtifactName(displayName, entry.artifactFileName)
        val tmpFile = File(dir, "$targetName.tmp")
        val targetFile = File(dir, targetName)
        inputStream.use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        return state(entry)
    }

    fun delete(entry: VoiceModelCatalogEntry): Boolean {
        return entry.modelDir().deleteRecursively()
    }

    private fun VoiceModelCatalogEntry.modelDir(): File {
        return File(rootDir, id)
    }

    companion object {
        private val UnsafeArtifactNameChars = Regex("""[\p{Cntrl}/\\:*?"<>|]+""")

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

    companion object {
        private const val VoiceModelsDirName = "voice-models"
    }
}
