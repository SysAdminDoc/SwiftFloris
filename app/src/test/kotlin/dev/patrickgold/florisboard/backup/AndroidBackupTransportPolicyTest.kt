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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class AndroidBackupTransportPolicyTest : FunSpec({
    test("every Android backup transport has an explicit export disposition") {
        AndroidBackupTransportPolicy.exportDispositions.keys shouldContainExactlyInAnyOrder
            AndroidBackupTransport.entries.toSet()
        AndroidBackupTransportPolicy.exportDispositions shouldBe mapOf(
            AndroidBackupTransport.Cloud to AndroidBackupExportDisposition.PortableInventory,
            AndroidBackupTransport.DeviceToDevice to AndroidBackupExportDisposition.PortableInventory,
            AndroidBackupTransport.CrossPlatformIos to AndroidBackupExportDisposition.Nothing,
        )
    }

    test("API 26 and 27 export nothing regardless of reported flags") {
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            apiLevel = 26,
            transportFlags = AndroidBackupTransportPolicy.ClientSideEncryptionFlag,
        ) shouldBe false
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            apiLevel = 27,
            transportFlags = AndroidBackupTransportPolicy.DeviceToDeviceFlag,
        ) shouldBe false
    }

    test("API 28 and newer cloud backup requires client-side encryption") {
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(apiLevel = 28, transportFlags = 0) shouldBe false
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            apiLevel = 28,
            transportFlags = AndroidBackupTransportPolicy.ClientSideEncryptionFlag,
        ) shouldBe true
    }

    test("API 28 and newer device transfer delegates to the portable XML allowlist") {
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            apiLevel = 28,
            transportFlags = AndroidBackupTransportPolicy.DeviceToDeviceFlag,
        ) shouldBe true
    }

    test("cross-platform and unknown transports export nothing") {
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            // Android 16 QPR2 exposes the API 36.1 transport flag while SDK_INT remains 36.
            apiLevel = 36,
            transportFlags = AndroidBackupTransportPolicy.CrossPlatformIosFlag,
        ) shouldBe false
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            apiLevel = 36,
            transportFlags = AndroidBackupTransportPolicy.CrossPlatformIosFlag or
                AndroidBackupTransportPolicy.ClientSideEncryptionFlag,
        ) shouldBe false
        AndroidBackupTransportPolicy.shouldDelegateFullBackup(
            apiLevel = 37,
            transportFlags = 1 shl 8,
        ) shouldBe false
    }

    test("cross-platform restore is rejected before writing a file") {
        AndroidBackupTransportPolicy.shouldDelegateRestore(
            AndroidBackupTransportPolicy.CrossPlatformIosFlag,
        ) shouldBe false
        AndroidBackupTransportPolicy.shouldDelegateRestore(0) shouldBe true
        AndroidBackupTransportPolicy.shouldDelegateRestore(
            AndroidBackupTransportPolicy.DeviceToDeviceFlag,
        ) shouldBe true
    }
})
