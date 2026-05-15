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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PairedSyncDevice(
    val deviceId: String,
    val displayName: String,
    val pubkeyHex: String,
    val syncChannelId: String,
    val pairedAtMillis: Long,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(pubkeyHex.matches(Regex("^[0-9a-f]{64}$"))) {
            "pubkeyHex must be 64 chars of lowercase hex"
        }
        require(syncChannelId.isNotBlank()) { "syncChannelId must not be blank" }
        require(pairedAtMillis >= 0) { "pairedAtMillis must be non-negative" }
    }

    companion object {
        fun fromPayload(payload: PairingPayload, pairedAtMillis: Long): PairedSyncDevice {
            return PairedSyncDevice(
                deviceId = payload.deviceId,
                displayName = payload.displayName,
                pubkeyHex = payload.pubkeyHex,
                syncChannelId = payload.syncChannelId,
                pairedAtMillis = pairedAtMillis,
            )
        }
    }
}

object PairedSyncDeviceList {
    private val JsonConfig = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun parse(rawJson: String): List<PairedSyncDevice> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching { JsonConfig.decodeFromString<List<PairedSyncDevice>>(rawJson) }
            .getOrElse { emptyList() }
            .distinctBy { it.deviceId }
    }

    fun serialize(devices: List<PairedSyncDevice>): String {
        return JsonConfig.encodeToString(devices.distinctBy { it.deviceId })
    }

    fun upsert(rawJson: String, device: PairedSyncDevice): String {
        val devices = parse(rawJson)
            .filterNot { it.deviceId == device.deviceId }
            .plus(device)
            .sortedBy { it.displayName.lowercase() }
        return serialize(devices)
    }
}
