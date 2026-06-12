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

package dev.patrickgold.florisboard.app.settings.sync

internal enum class SyncCompatibilityRoute {
    SealedDeviceSync,
    PassphraseDictionaryMigration,
}

internal data class SyncCompatibilityState(
    val route: SyncCompatibilityRoute,
) {
    val supportsSealedDeviceSync: Boolean
        get() = route == SyncCompatibilityRoute.SealedDeviceSync

    val usesPassphraseDictionaryMigration: Boolean
        get() = route == SyncCompatibilityRoute.PassphraseDictionaryMigration
}

internal object SyncCompatibilityPolicy {
    const val MIN_SEALED_DEVICE_SYNC_SDK: Int = 33

    fun stateForSdk(sdkInt: Int): SyncCompatibilityState {
        return SyncCompatibilityState(
            route = if (sdkInt >= MIN_SEALED_DEVICE_SYNC_SDK) {
                SyncCompatibilityRoute.SealedDeviceSync
            } else {
                SyncCompatibilityRoute.PassphraseDictionaryMigration
            },
        )
    }
}
