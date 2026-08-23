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

package dev.patrickgold.florisboard.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.app.backup.FullRestoreDataInput
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi

/**
 * Keeps Android-managed backup on the platform's file allowlists while rejecting transports that
 * cannot meet SwiftFloris's privacy contract. The key-value callbacks are intentionally empty;
 * `android:fullBackupOnly` selects Auto Backup on every supported Android release.
 */
class SwiftFlorisBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (!AndroidBackupTransportPolicy.shouldDelegateFullBackup(
                apiLevel = Build.VERSION.SDK_INT,
                transportFlags = Api28Impl.transportFlags(data),
            )
        ) {
            return
        }
        super.onFullBackup(data)
    }

    /** Android 16 QPR2 supplies transport identity on each restored file. */
    @RequiresApi(37)
    override fun onRestoreFile(data: FullRestoreDataInput) {
        if (AndroidBackupTransportPolicy.shouldDelegateRestore(data.transportFlags)) {
            super.onRestoreFile(data)
        }
    }

    @RequiresApi(28)
    private object Api28Impl {
        fun transportFlags(data: FullBackupDataOutput): Int = data.transportFlags
    }
}
