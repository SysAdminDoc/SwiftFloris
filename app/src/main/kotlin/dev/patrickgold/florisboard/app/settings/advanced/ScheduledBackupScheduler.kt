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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object ScheduledBackupScheduler {
    const val UniqueWorkName = "swiftfloris_scheduled_backup"

    fun schedule(context: Context, cadence: ScheduledBackupCadence) {
        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(
            cadence.repeatHours,
            TimeUnit.HOURS,
        )
            // Delayed work is intentionally offline-only. No network
            // constraint is ever attached to this request.
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext ?: context)
            .enqueueUniquePeriodicWork(
                UniqueWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext ?: context)
            .cancelUniqueWork(UniqueWorkName)
    }

    fun reconcile(context: Context) {
        val settings = ScheduledBackupStore.load(context)
        if (settings.enabled) {
            schedule(context, settings.cadence)
        } else {
            cancel(context)
        }
    }
}
