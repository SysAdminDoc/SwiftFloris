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

package dev.patrickgold.florisboard.ime.sync

import java.security.KeyPair
import java.util.Locale
import java.util.UUID

object PairingPayloadGenerator {
    fun generate(
        displayName: String,
        syncChannelId: String,
        clusterId: String = UUID.randomUUID().toString(),
        deviceId: String = UUID.randomUUID().toString(),
        keyPair: KeyPair = SealedBoxCrypto.generateKeyPair(),
        senderClock: Long = System.currentTimeMillis(),
    ): PairingPayload {
        return PairingPayload(
            schema = PairingPayload.SUPPORTED_SCHEMA,
            clusterId = clusterId,
            deviceId = deviceId,
            pubkeyHex = keyPair.public.encoded.takeLast(PUBKEY_LENGTH).toByteArray().toLowerHex(),
            displayName = displayName.ifBlank { "Android device" },
            syncChannelId = syncChannelId,
            senderClock = senderClock,
        )
    }

    private fun ByteArray.toLowerHex(): String {
        return joinToString(separator = "") { byte ->
            String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
        }
    }

    private const val PUBKEY_LENGTH = 32
}
