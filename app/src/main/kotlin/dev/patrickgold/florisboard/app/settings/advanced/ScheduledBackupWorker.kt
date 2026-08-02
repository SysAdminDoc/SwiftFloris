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
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.initializePreferenceStoreForStartup
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File

internal class ScheduledBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settings = ScheduledBackupStore.load(applicationContext)
        if (!settings.enabled) return Result.success()

        val passphrase = runCatching { ScheduledBackupStore.readPassphrase(applicationContext) }
            .getOrNull()
        if (passphrase == null) {
            val error = IllegalStateException("Scheduled backup passphrase is unavailable.")
            ScheduledBackupStore.recordFailure(applicationContext, error)
            ScheduledBackupNotifications.showFailure(applicationContext, error)
            return Result.failure()
        }

        val workspaceRoot = File(
            applicationContext.cacheDir,
            "scheduled-backup/${id}",
        )
        return try {
            val treeUri = requireNotNull(settings.treeUri.takeIf { it.isNotBlank() }) {
                "Scheduled backup folder is not configured."
            }.let(Uri::parse)
            requirePersistedWriteGrant(applicationContext, treeUri)
            awaitPreferenceStore(applicationContext)
            val built = BackupArchiveBuilder.build(
                context = applicationContext,
                inputDir = File(workspaceRoot, "input"),
                outputDir = File(workspaceRoot, "output"),
                selection = Backup.scheduledSelection(),
                passphrase = passphrase,
                forceEncryption = true,
            )
            val published = ScheduledBackupSaf.publish(
                context = applicationContext,
                treeUri = treeUri,
                archive = built.archiveFile,
                finalName = ScheduledBackupPolicy.archiveName(
                    versionCode = built.metadata.versionCode,
                    timestamp = built.metadata.timestamp,
                ),
            )
            ScheduledBackupSaf.pruneVerified(
                context = applicationContext,
                treeUri = treeUri,
                retentionCount = settings.retentionCount,
                passphrase = passphrase,
            )
            ScheduledBackupStore.recordSuccess(applicationContext, published.name)
            ScheduledBackupNotifications.showSuccess(applicationContext, published.name)
            Result.success()
        } catch (error: Throwable) {
            ScheduledBackupStore.recordFailure(applicationContext, error)
            ScheduledBackupNotifications.showFailure(applicationContext, error)
            Result.failure()
        } finally {
            passphrase.fill('\u0000')
            workspaceRoot.deleteRecursively()
        }
    }

    private suspend fun awaitPreferenceStore(context: Context) {
        val application = context.applicationContext as? FlorisApplication
        if (application != null) {
            withTimeout(30_000L) {
                application.preferenceStoreLoaded.first { it }
            }
        } else {
            initializePreferenceStoreForStartup(
                context = context,
                preferenceStoreLoaded = MutableStateFlow(false),
                datastoreName = FlorisPreferenceModel.NAME,
            )
        }
    }

    private fun requirePersistedWriteGrant(context: Context, treeUri: Uri) {
        check(android.provider.DocumentsContract.isTreeUri(treeUri)) {
            "Scheduled backup folder is not a SAF tree URI."
        }
        val permission = context.contentResolver.persistedUriPermissions.firstOrNull {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        check(permission != null) {
            "Scheduled backup folder permission is no longer available."
        }
    }
}
