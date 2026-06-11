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

package dev.patrickgold.florisboard.benchmark

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.advanced.Backup
import dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicy
import dev.patrickgold.florisboard.app.settings.advanced.Restore
import dev.patrickgold.florisboard.app.settings.advanced.RestoreOperationSummary
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

class BenchmarkBackupRestoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "SwiftFloris backup/restore benchmark" })

        lifecycleScope.launch {
            try {
                val app = applicationContext as FlorisApplication
                withTimeout(10_000) {
                    app.preferenceStoreLoaded.filter { it }.first()
                }

                val cacheManager by cacheManager()
                val result = withContext(Dispatchers.IO) {
                    seedRepresentativeArchiveData()
                    val backup = createBackup(cacheManager)
                    try {
                        val restore = restoreBackup(cacheManager, backup.archive)
                        backup to restore
                    } finally {
                        backup.workspace.close()
                    }
                }
                logBackup(result.first)
                logRestore(result.second, result.first.archiveBytes)
                setResult(Activity.RESULT_OK)
            } catch (error: Throwable) {
                Log.e("SwiftFlorisPerf", "swiftfloris.backupRestore.benchmarkFailed", error)
                setResult(Activity.RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }

    private fun seedRepresentativeArchiveData() {
        seedFixtureDir(
            root = filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).subDir(BenchmarkFixtureDir),
            prefix = "keyboard",
            fileCount = 24,
            payloadLines = 36,
        )
        seedFixtureDir(
            root = filesDir.subDir(ExtensionManager.IME_THEME_PATH).subDir(BenchmarkFixtureDir),
            prefix = "theme",
            fileCount = 24,
            payloadLines = 54,
        )
    }

    private fun seedFixtureDir(
        root: FsDir,
        prefix: String,
        fileCount: Int,
        payloadLines: Int,
    ) {
        root.mkdirs()
        root.deleteContentsRecursively()
        repeat(fileCount) { index ->
            val body = buildString {
                appendLine("{")
                appendLine("  \"id\": \"$prefix-$index\",")
                appendLine("  \"label\": \"Benchmark $prefix $index\",")
                appendLine("  \"payload\": [")
                repeat(payloadLines) { line ->
                    append("    \"$prefix-$index-line-$line")
                    append("-abcdefghijklmnopqrstuvwxyz0123456789\"")
                    appendLine(if (line == payloadLines - 1) "" else ",")
                }
                appendLine("  ]")
                appendLine("}")
            }
            root.subFile("$prefix-$index.json").writeText(body)
        }
    }

    private suspend fun createBackup(
        cacheManager: CacheManager,
    ): BackupMetrics {
        val workspace = cacheManager.backupAndRestore.new()
        val startedAt = SystemClock.elapsedRealtimeNanos()
        try {
            val fileBasedStorage = workspace.inputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                .let { FileBasedStorage(it.path) }
            FlorisPreferenceStore.export(fileBasedStorage).getOrThrow()

            val workspaceFilesDir = workspace.inputDir.subDir("files")
            filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).copyRecursively(
                workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH),
            )
            filesDir.subDir(ExtensionManager.IME_THEME_PATH).copyRecursively(
                workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH),
            )

            workspace.metadata = Backup.Metadata(
                packageName = BuildConfig.APPLICATION_ID,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                timestamp = System.currentTimeMillis(),
            )
            workspace.inputDir.subFile(Backup.METADATA_JSON_NAME).writeJson(workspace.metadata)
            workspace.zipFile = workspace.outputDir.subFile(Backup.defaultFileName(workspace.metadata))
            ZipUtils.zip(workspace.inputDir, workspace.zipFile)
            val durationMs = elapsedMsSince(startedAt)
            return BackupMetrics(
                workspace = workspace,
                archive = workspace.zipFile,
                archiveBytes = workspace.zipFile.length(),
                createMs = durationMs,
                selectedSections = RepresentativeSectionCount,
            )
        } catch (error: Throwable) {
            workspace.close()
            throw error
        }
    }

    private suspend fun restoreBackup(
        cacheManager: CacheManager,
        archive: FsFile,
    ): RestoreMetrics {
        val workspace = cacheManager.backupAndRestore.new()
        try {
            val prepareStartedAt = SystemClock.elapsedRealtimeNanos()
            workspace.zipFile = workspace.inputDir.subFile(Restore.BACKUP_ARCHIVE_FILE_NAME)
            archive.copyTo(workspace.zipFile, overwrite = true)
            ZipUtils.unzip(workspace.zipFile, workspace.outputDir)
            val metadata: Backup.Metadata = workspace.outputDir.subFile(Backup.METADATA_JSON_NAME).readJson()
            workspace.metadata = metadata

            val workspaceFilesDir = workspace.outputDir.subDir("files")
            val validation = BackupRestorePolicy.validateRestoreArchive(
                metadata = metadata,
                currentVersionCode = BuildConfig.VERSION_CODE,
                minimumVersionCode = Restore.MIN_VERSION_CODE,
                expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
                hasRestorableContent = BackupRestorePolicy.hasRestorableContent(
                    hasJetprefDatastore = workspace.outputDir
                        .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                        .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
                        .exists(),
                    hasImeKeyboard = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).exists(),
                    hasImeTheme = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH).exists(),
                    hasClipboardTextItems = false,
                    hasClipboardImageItems = false,
                    hasClipboardVideoItems = false,
                ),
            )
            workspace.restoreWarningId = validation.warningId
            workspace.restoreErrorId = validation.errorId
            check(validation.errorId == null) { "Representative archive failed restore validation: ${validation.errorId}" }
            val prepareMs = elapsedMsSince(prepareStartedAt)

            val applyStartedAt = SystemClock.elapsedRealtimeNanos()
            val summary = applyRepresentativeRestore(workspace)
            val applyMs = elapsedMsSince(applyStartedAt)
            return RestoreMetrics(
                prepareMs = prepareMs,
                applyMs = applyMs,
                totalMs = prepareMs + applyMs,
                summary = summary,
                strategy = ImportStrategy.Merge.name,
            )
        } finally {
            workspace.close()
        }
    }

    private suspend fun applyRepresentativeRestore(
        workspace: CacheManager.BackupAndRestoreWorkspace,
    ): RestoreOperationSummary {
        var summary = RestoreOperationSummary()

        suspend fun restoreSelectedSection(
            sourceExists: Boolean,
            block: suspend () -> Unit,
        ) {
            summary = summary.copy(selectedSections = summary.selectedSections + 1)
            if (!sourceExists) {
                summary = summary.copy(missingSections = summary.missingSections + 1)
                return
            }
            runCatching {
                block()
            }.onSuccess {
                summary = summary.copy(restoredSections = summary.restoredSections + 1)
            }.onFailure { error ->
                summary = summary.copy(
                    failedSections = summary.failedSections + 1,
                    firstFailureMessage = summary.firstFailureMessage ?: error.localizedMessage,
                )
            }
        }

        val datastore = workspace.outputDir
            .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
            .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
        restoreSelectedSection(sourceExists = datastore.exists()) {
            FlorisPreferenceStore.import(
                ImportStrategy.Merge,
                FileBasedStorage(datastore.path),
            ).getOrThrow()
        }

        val workspaceFilesDir = workspace.outputDir.subDir("files")
        restoreSelectedSection(
            sourceExists = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).exists(),
        ) {
            workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH).copyRecursively(
                filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH),
                overwrite = true,
            )
        }
        restoreSelectedSection(
            sourceExists = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH).exists(),
        ) {
            workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH).copyRecursively(
                filesDir.subDir(ExtensionManager.IME_THEME_PATH),
                overwrite = true,
            )
        }

        return summary
    }

    private fun logBackup(metrics: BackupMetrics) {
        Log.i(
            "SwiftFlorisPerf",
            "swiftfloris.backup.createMs=${metrics.createMs} " +
                "archiveBytes=${metrics.archiveBytes} " +
                "sections=${metrics.selectedSections} profile=$RepresentativeProfile",
        )
    }

    private fun logRestore(metrics: RestoreMetrics, archiveBytes: Long) {
        Log.i(
            "SwiftFlorisPerf",
            "swiftfloris.restore.prepareMs=${metrics.prepareMs} " +
                "archiveBytes=$archiveBytes profile=$RepresentativeProfile",
        )
        Log.i(
            "SwiftFlorisPerf",
            "swiftfloris.restore.applyMs=${metrics.applyMs} " +
                "selectedSections=${metrics.summary.selectedSections} " +
                "restoredSections=${metrics.summary.restoredSections} " +
                "missingSections=${metrics.summary.missingSections} " +
                "failedSections=${metrics.summary.failedSections} " +
                "strategy=${metrics.strategy} profile=$RepresentativeProfile",
        )
        Log.i(
            "SwiftFlorisPerf",
            "swiftfloris.restore.totalMs=${metrics.totalMs} " +
                "archiveBytes=$archiveBytes profile=$RepresentativeProfile",
        )
    }

    private fun elapsedMsSince(startedAt: Long): Double {
        return (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
    }

    private data class BackupMetrics(
        val workspace: CacheManager.BackupAndRestoreWorkspace,
        val archive: FsFile,
        val archiveBytes: Long,
        val createMs: Double,
        val selectedSections: Int,
    )

    private data class RestoreMetrics(
        val prepareMs: Double,
        val applyMs: Double,
        val totalMs: Double,
        val summary: RestoreOperationSummary,
        val strategy: String,
    )

    companion object {
        private const val BenchmarkFixtureDir = "benchmark-fixture"
        private const val RepresentativeProfile = "settingsKeyboardTheme"
        private const val RepresentativeSectionCount = 3
    }
}
