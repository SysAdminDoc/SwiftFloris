/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.lib.cache

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.app.ext.EditorAction
import dev.patrickgold.florisboard.app.settings.advanced.Backup
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.theme.ThemeExtensionEditor
import dev.patrickgold.florisboard.lib.NATIVE_NULLPTR
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionDefaults
import dev.patrickgold.florisboard.lib.ext.ExtensionEditor
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.lib.ext.ExtensionPackagePolicy
import dev.patrickgold.florisboard.lib.io.FileRegistry
import dev.patrickgold.florisboard.lib.io.ZipUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.android.query
import org.florisboard.lib.android.readToFile
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import java.io.Closeable
import java.util.UUID

class CacheManager(context: Context) {
    companion object {
        private const val InputDirName = "input"
        private const val OutputDirName = "output"

        private const val ImporterDirName = "importer"
        private const val ExporterDirName = "exporter"
        private const val EditorDirName = "editor"
        private const val BackupAndRestoreDirName = "backup-and-restore"

        const val LoadedDirName = "loaded"

        internal const val MaxImportUriCount = 32
        internal const val MaxImportFileBytes = 256L * 1024L * 1024L
        internal const val MaxImportBatchBytes = 512L * 1024L * 1024L
        internal const val SharedBackupGrantLeaseMillis = 15L * 60L * 1000L

        private val UnsafeImportFileNameChars = Regex("""[\p{Cntrl}/\\:*?"<>|]+""")

        internal fun requireImportUriCount(size: Int) {
            check(size <= MaxImportUriCount) {
                "Import batch contains $size files; maximum is $MaxImportUriCount."
            }
        }

        internal fun requireImportBatchCapacity(currentBytes: Long, nextBytes: Long) {
            if (nextBytes <= 0L) return
            check(nextBytes <= MaxImportBatchBytes && currentBytes <= MaxImportBatchBytes - nextBytes) {
                "Import batch exceeds maximum size of $MaxImportBatchBytes bytes."
            }
        }

        internal fun addImportBatchBytes(currentBytes: Long, nextBytes: Long): Long {
            requireImportBatchCapacity(currentBytes, nextBytes)
            return currentBytes + nextBytes.coerceAtLeast(0L)
        }

        private fun directoryFileBytes(dir: FsDir): Long {
            if (!dir.exists()) return 0L
            var totalBytes = 0L
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalBytes = addImportBatchBytes(totalBytes, file.length())
                }
            }
            return totalBytes
        }

        internal fun sanitizeImportFileName(displayName: String?, fallbackName: String): String {
            val sanitized = displayName
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(UnsafeImportFileNameChars, "_")
                ?.trim()
                ?.trim('.')
                ?.take(128)
                ?.takeIf { it.isNotBlank() }
            return sanitized ?: fallbackName
        }

        internal fun uniqueImportFileName(fileName: String, usedNames: Set<String>, dir: FsDir): String {
            fun isAvailable(name: String): Boolean {
                return name !in usedNames && !dir.subFile(name).exists()
            }
            if (isAvailable(fileName)) return fileName
            val dotIndex = fileName.lastIndexOf('.').takeIf { it > 0 && it < fileName.lastIndex }
            val stem = dotIndex?.let { fileName.substring(0, it) } ?: fileName
            val ext = dotIndex?.let { fileName.substring(it) } ?: ""
            for (n in 2..999) {
                val candidate = "$stem-$n$ext"
                if (isAvailable(candidate)) return candidate
            }
            return "$stem-${UUID.randomUUID()}$ext"
        }

        private fun Cursor.getStringOrNull(columnName: String): String? {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getString(index) else null
        }

        private fun Cursor.getLongOrNull(columnName: String): Long? {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getLong(index) else null
        }
    }

    private val appContext by context.appContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val importer = WorkspacesContainer(ImporterDirName) { ImporterWorkspace(it) }
    val exporter = WorkspacesContainer(ExporterDirName) { ExporterWorkspace(it) }
    val themeExtEditor = WorkspacesContainer(EditorDirName) { ExtEditorWorkspace<ThemeExtensionEditor>(it) }
    val backupAndRestore = WorkspacesContainer(BackupAndRestoreDirName) { BackupAndRestoreWorkspace(it) }

    init {
        // Capture before this CacheManager can hand out a new workspace. These
        // directories can only belong to an earlier process, so no age grace
        // is needed for plaintext restore staging or expired shared exports.
        val abandonedBackupWorkspaces = backupAndRestore.dir.listFiles().orEmpty()
            .filter { it.isDirectory }
        scope.launch {
            abandonedBackupWorkspaces.forEach { staleDir ->
                runCatching { staleDir.deleteRecursively() }
            }
        }
    }

    /**
     * Keep a shared backup artifact available only for a bounded receiver
     * window. Android also revokes temporary grants when the receiver's task
     * finishes; this lease is the deterministic upper bound while our process
     * remains alive. Process-death leftovers are swept on the next startup.
     */
    fun leaseSharedBackupArtifact(
        workspace: BackupAndRestoreWorkspace,
        uri: Uri,
    ) {
        workspace.dir.setLastModified(System.currentTimeMillis())
        scope.launch {
            delay(SharedBackupGrantLeaseMillis)
            runCatching {
                appContext.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            workspace.close()
        }
    }

    fun readFromUriIntoCache(uri: Uri) = readFromUriIntoCache(listOf(uri))

    fun readFromUriIntoCache(uriList: List<Uri>): ImporterWorkspace {
        val importUris = uriList.distinct()
        requireImportUriCount(importUris.size)
        val contentResolver = appContext.contentResolver ?: error("Content resolver is null.")
        val workspace = ImporterWorkspace(uuid = UUID.randomUUID().toString()).also { it.mkdirs() }
        try {
            val usedFileNames = mutableSetOf<String>()
            var totalImportBytes = 0L
            workspace.inputFileInfos = buildList {
                for ((index, uri) in importUris.withIndex()) {
                    val fallbackFileName = "import-${index + 1}"
                    val (displayName, reportedSize) = contentResolver.query(uri)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME) to cursor.getLongOrNull(OpenableColumns.SIZE)
                        } else {
                            null to null
                        }
                    } ?: (null to null)
                    reportedSize?.takeIf { it >= 0L }?.let {
                        requireImportBatchCapacity(totalImportBytes, it)
                    }
                    val fileName = uniqueImportFileName(
                        fileName = sanitizeImportFileName(displayName ?: uri.lastPathSegment, fallbackFileName),
                        usedNames = usedFileNames,
                        dir = workspace.inputDir,
                    )
                    usedFileNames += fileName
                    val file = workspace.inputDir.subFile(fileName)
                    contentResolver.readToFile(uri, file, MaxImportFileBytes)
                    totalImportBytes = addImportBatchBytes(totalImportBytes, file.length())
                    val extWorkingDir = workspace.outputDir.subDir(file.nameWithoutExtension)
                    val ext = runCatching {
                        ZipUtils.unzip(srcFile = file, dstDir = extWorkingDir)
                        val extJsonFile = extWorkingDir.subFile(ExtensionDefaults.MANIFEST_FILE_NAME)
                        ExtensionPackagePolicy.requireManifestSize(extJsonFile.length())
                        extJsonFile.readJson<Extension>(ExtensionJsonConfig).also { extension ->
                            extension.workingDir = extWorkingDir
                            ExtensionPackagePolicy.validateExtracted(extension, extWorkingDir)
                        }
                    }
                    totalImportBytes = addImportBatchBytes(totalImportBytes, directoryFileBytes(extWorkingDir))
                    if (ext.isFailure) {
                        extWorkingDir.deleteRecursively()
                    }
                    add(
                        FileInfo(
                            file = file,
                            mediaType = FileRegistry.guessMediaType(file, contentResolver.getType(uri)),
                            size = reportedSize?.takeIf { it >= 0L } ?: file.length(),
                            ext = ext.getOrNull(),
                        )
                    )
                }
            }
            importer.add(workspace)
            return workspace
        } catch (error: Throwable) {
            workspace.close()
            throw error
        }
    }

    open inner class WorkspacesContainer<T : Workspace> internal constructor(
        val dirName: String,
        val factory: (uuid: String) -> T,
    ) {
        private val workspacesGuard = Mutex(locked = false)
        private val workspaces = mutableListOf<T>()

        val dir: FsDir = appContext.cacheDir.subDir(dirName)

        fun new(uuid: String = UUID.randomUUID().toString()): T {
            return factory(uuid).also { it.mkdirs(); add(it) }
        }

        internal fun add(workspace: T) = runBlocking {
            workspacesGuard.withLock {
                if (workspaces.none { it.uuid == workspace.uuid }) {
                    workspaces.add(workspace)
                }
            }
        }

        internal fun remove(workspace: T) = runBlocking {
            workspacesGuard.withLock {
                workspaces.remove(workspace)
            }
        }

        internal fun removeByUuid(uuid: String) = runBlocking {
            workspacesGuard.withLock {
                workspaces.removeAll { it.uuid == uuid }
            }
        }

        fun getWorkspaceByUuid(uuid: String) = runBlocking { getWorkspaceByUuidAsync(uuid).await() }

        fun getWorkspaceByUuidAsync(uuid: String): Deferred<T?> = scope.async {
            workspacesGuard.withLock {
                workspaces.find { it.uuid == uuid }
            }
        }
    }

    abstract inner class Workspace(val uuid: String) : Closeable {
        abstract val dir: FsDir

        open fun mkdirs() {
            dir.mkdirs()
        }

        fun isOpen() = dir.exists()

        fun isClosed() = !dir.exists()

        override fun close() {
            dir.deleteRecursively()
        }
    }

    inner class ImporterWorkspace(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = importer.dir.subDir(uuid)

        val inputDir: FsDir = dir.subDir(InputDirName)
        val outputDir: FsDir = dir.subDir(OutputDirName)

        var inputFileInfos = emptyList<FileInfo>()

        override fun mkdirs() {
            super.mkdirs()
            inputDir.mkdirs()
            outputDir.mkdirs()
        }

        override fun close() {
            super.close()
            importer.remove(this)
        }
    }

    inner class ExporterWorkspace(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = exporter.dir.subDir(uuid)
    }

    inner class ExtEditorWorkspace<T : ExtensionEditor>(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = themeExtEditor.dir.subDir(uuid)

        val extDir: FsDir = dir.subDir("ext")
        val saverDir: FsDir = dir.subDir("saver")

        var currentAction by mutableStateOf<EditorAction?>(null)
        var ext: Extension? = null
        var editor by mutableStateOf<T?>(null)
        var version by mutableIntStateOf(0)

        val isModified get() = version > 0

        override fun mkdirs() {
            super.mkdirs()
            extDir.mkdirs()
            saverDir.mkdirs()
        }

        override fun close() {
            try {
                super.close()
            } finally {
                themeExtEditor.removeByUuid(uuid)
            }
        }

        inline fun <R> update(block: T.() -> R): R {
            // Method is designed to only be called when editor has been previously initialized
            val ret = block(editor!!)
            version++
            return ret
        }
    }

    inner class BackupAndRestoreWorkspace(uuid: String) : Workspace(uuid) {
        override val dir: FsDir = backupAndRestore.dir.subDir(uuid)

        val inputDir: FsDir = dir.subDir(InputDirName)
        val outputDir: FsDir = dir.subDir(OutputDirName)

        lateinit var archiveFile: FsFile
        lateinit var metadata: Backup.Metadata
        var archiveWasEncrypted: Boolean = false
        var archiveWasLegacyPlaintext: Boolean = false
        var restoreWarningId: Int? = null
        var restoreErrorId: Int? = null

        override fun mkdirs() {
            super.mkdirs()
            inputDir.mkdirs()
            outputDir.mkdirs()
        }

        override fun close() {
            super.close()
            backupAndRestore.remove(this)
        }
    }

    data class FileInfo(
        val file: FsFile,
        val mediaType: String?,
        val size: Long,
        val ext: Extension?,
        var skipReason: Int = NATIVE_NULLPTR.toInt(),
    )
}
