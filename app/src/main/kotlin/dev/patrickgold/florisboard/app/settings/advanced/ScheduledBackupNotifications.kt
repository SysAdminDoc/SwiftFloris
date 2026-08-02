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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R

internal object ScheduledBackupNotifications {
    private const val ChannelId = "scheduled_backup_status"
    private const val NotificationId = 0x534642

    fun showSuccess(context: Context, archiveName: String) {
        notify(
            context = context,
            title = context.getString(R.string.scheduled_backup__notification_success_title),
            message = context.getString(
                R.string.scheduled_backup__notification_success_summary,
                archiveName,
            ),
            isError = false,
        )
    }

    fun showFailure(context: Context, error: Throwable) {
        notify(
            context = context,
            title = context.getString(R.string.scheduled_backup__notification_failure_title),
            message = context.getString(
                R.string.scheduled_backup__notification_failure_summary,
                (error.message ?: error::class.java.simpleName).take(220),
            ),
            isError = true,
        )
    }

    private fun notify(
        context: Context,
        title: String,
        message: String,
        isError: Boolean,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val appContext = context.applicationContext ?: context
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                appContext.getString(R.string.scheduled_backup__notification_channel),
                if (isError) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.notify(
            NotificationId,
            NotificationCompat.Builder(appContext, ChannelId)
                .setSmallIcon(R.drawable.ic_floris_monochrome)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build(),
        )
    }
}
