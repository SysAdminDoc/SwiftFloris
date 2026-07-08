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

object PairingPayloadReceiver {
    sealed interface Plan {
        data class Accepted(
            val clusterId: String,
            val deviceId: String,
            val pairedDevicesJson: String,
            val foldedLocalState: PersonalDictionaryCrdt,
        ) : Plan

        data class ClusterMismatch(
            val localClusterId: String,
            val payloadClusterId: String,
        ) : Plan
    }

    fun plan(
        payload: PairingPayload,
        localClusterId: String,
        localDeviceId: String,
        pairedDevicesJson: String,
        previousLocalState: PersonalDictionaryCrdt?,
        pairedAtMillis: Long,
        newDeviceId: () -> String,
    ): Plan {
        val trimmedLocalClusterId = localClusterId.trim()
        if (trimmedLocalClusterId.isNotEmpty() && trimmedLocalClusterId != payload.clusterId) {
            return Plan.ClusterMismatch(
                localClusterId = trimmedLocalClusterId,
                payloadClusterId = payload.clusterId,
            )
        }

        val resolvedDeviceId = localDeviceId.ifBlank {
            previousLocalState?.deviceId?.takeIf { it.isNotBlank() } ?: newDeviceId()
        }
        val resolvedClusterId = trimmedLocalClusterId.ifBlank { payload.clusterId }
        val foldedLocalState = (previousLocalState ?: PersonalDictionaryCrdt(
            deviceId = resolvedDeviceId,
            clock = payload.senderClock,
        )).let { state ->
            state.copy(
                deviceId = resolvedDeviceId,
                clock = maxOf(state.clock, payload.senderClock),
            )
        }

        return Plan.Accepted(
            clusterId = resolvedClusterId,
            deviceId = resolvedDeviceId,
            pairedDevicesJson = PairedSyncDeviceList.upsert(
                pairedDevicesJson,
                PairedSyncDevice.fromPayload(payload, pairedAtMillis),
            ),
            foldedLocalState = foldedLocalState,
        )
    }
}
