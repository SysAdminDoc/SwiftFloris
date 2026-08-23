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

internal enum class AndroidBackupTransport {
    Cloud,
    DeviceToDevice,
    CrossPlatformIos,
}

internal enum class AndroidBackupExportDisposition {
    PortableInventory,
    Nothing,
}

/**
 * Defense in depth around Android's XML backup allowlists.
 *
 * API 26 and 27 provide no client-side-encryption transport flag, so they export nothing. Newer
 * cloud transports must report client-side encryption, D2D may use the portable XML allowlist,
 * and iOS or unknown future transports fail closed.
 */
internal object AndroidBackupTransportPolicy {
    const val ClientSideEncryptionFlag = 1
    const val DeviceToDeviceFlag = 1 shl 1
    const val CrossPlatformIosFlag = 1 shl 3

    private const val KnownTransportFlags =
        ClientSideEncryptionFlag or DeviceToDeviceFlag or CrossPlatformIosFlag

    val exportDispositions: Map<AndroidBackupTransport, AndroidBackupExportDisposition> = mapOf(
        AndroidBackupTransport.Cloud to AndroidBackupExportDisposition.PortableInventory,
        AndroidBackupTransport.DeviceToDevice to AndroidBackupExportDisposition.PortableInventory,
        AndroidBackupTransport.CrossPlatformIos to AndroidBackupExportDisposition.Nothing,
    )

    fun shouldDelegateFullBackup(apiLevel: Int, transportFlags: Int): Boolean {
        if (apiLevel < 28 || hasUnknownFlags(transportFlags)) return false
        if (transportFlags and CrossPlatformIosFlag != 0) return false
        if (transportFlags and DeviceToDeviceFlag != 0) return true
        return transportFlags and ClientSideEncryptionFlag != 0
    }

    fun shouldDelegateRestore(transportFlags: Int): Boolean {
        if (hasUnknownFlags(transportFlags)) return false
        return transportFlags and CrossPlatformIosFlag == 0
    }

    private fun hasUnknownFlags(transportFlags: Int): Boolean {
        return transportFlags and KnownTransportFlags.inv() != 0
    }
}
