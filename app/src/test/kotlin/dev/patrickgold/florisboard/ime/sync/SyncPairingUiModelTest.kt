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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SyncPairingUiModelTest : FunSpec({
    test("PairingPayloadGenerator emits a parseable v1 payload for the selected channel") {
        val payload = PairingPayloadGenerator.generate(
            displayName = "Pixel Tablet",
            syncChannelId = SyncChannel.Syncthing("swiftfloris-home").channelId,
            clusterId = "cluster-1",
            deviceId = "device-1",
            senderClock = 42L,
        )

        payload.schema shouldBe PairingPayload.SUPPORTED_SCHEMA
        payload.displayName shouldBe "Pixel Tablet"
        payload.syncChannelId shouldBe "swiftfloris:syncthing:swiftfloris-home"
        payload.pubkeyHex.matches(Regex("^[0-9a-f]{64}$")) shouldBe true

        PairingPayload.parse(payload.serializeToString()).shouldNotBeNull() shouldBe payload
    }

    test("PairingPayloadGenerator falls back to a non-blank display name") {
        val payload = PairingPayloadGenerator.generate(
            displayName = " ",
            syncChannelId = SyncChannel.Disabled.channelId,
            clusterId = "cluster-1",
            deviceId = "device-1",
            senderClock = 42L,
        )

        payload.displayName shouldBe "Android device"
    }

    test("PairedSyncDeviceList upserts by device id and keeps deterministic display-name order") {
        val first = PairedSyncDevice(
            deviceId = "b",
            displayName = "Tablet",
            pubkeyHex = "b".repeat(64),
            syncChannelId = SyncChannel.Disabled.channelId,
            pairedAtMillis = 1L,
        )
        val second = PairedSyncDevice(
            deviceId = "a",
            displayName = "Phone",
            pubkeyHex = "a".repeat(64),
            syncChannelId = SyncChannel.Syncthing("home").channelId,
            pairedAtMillis = 2L,
        )
        val replacement = second.copy(displayName = "Laptop", pairedAtMillis = 3L)

        val raw = PairedSyncDeviceList.upsert(
            PairedSyncDeviceList.upsert("[]", first),
            second,
        )
        val replaced = PairedSyncDeviceList.parse(PairedSyncDeviceList.upsert(raw, replacement))

        replaced.map { it.displayName } shouldContainExactly listOf("Laptop", "Tablet")
        replaced.map { it.deviceId } shouldContainExactly listOf("a", "b")
        replaced.first().pairedAtMillis shouldBe 3L
    }

    test("PairedSyncDeviceList tolerates corrupt persisted JSON") {
        PairedSyncDeviceList.parse("{not-json") shouldBe emptyList()
    }

    test("PairingPayloadReceiver adopts a peer cluster for an unclustered local device") {
        val payload = PairingPayloadGenerator.generate(
            displayName = "Phone",
            syncChannelId = SyncChannel.ManualExport.channelId,
            clusterId = "cluster-peer",
            deviceId = "peer-device",
            senderClock = 900L,
        )

        val accepted = PairingPayloadReceiver.plan(
            payload = payload,
            localClusterId = "",
            localDeviceId = "",
            pairedDevicesJson = "[]",
            previousLocalState = null,
            pairedAtMillis = 123L,
            newDeviceId = { "local-device" },
        ).shouldBeInstanceOf<PairingPayloadReceiver.Plan.Accepted>()

        accepted.clusterId shouldBe "cluster-peer"
        accepted.deviceId shouldBe "local-device"
        accepted.foldedLocalState shouldBe PersonalDictionaryCrdt(
            deviceId = "local-device",
            clock = 900L,
        )
        PairedSyncDeviceList.parse(accepted.pairedDevicesJson).single() shouldBe PairedSyncDevice(
            deviceId = "peer-device",
            displayName = "Phone",
            pubkeyHex = payload.pubkeyHex,
            syncChannelId = SyncChannel.ManualExport.channelId,
            pairedAtMillis = 123L,
        )
    }

    test("PairingPayloadReceiver rejects a payload from a different existing cluster") {
        val payload = PairingPayloadGenerator.generate(
            displayName = "Phone",
            syncChannelId = SyncChannel.ManualExport.channelId,
            clusterId = "cluster-peer",
            deviceId = "peer-device",
            senderClock = 900L,
        )

        val mismatch = PairingPayloadReceiver.plan(
            payload = payload,
            localClusterId = "cluster-local",
            localDeviceId = "local-device",
            pairedDevicesJson = "[]",
            previousLocalState = PersonalDictionaryCrdt(deviceId = "local-device", clock = 50L),
            pairedAtMillis = 123L,
            newDeviceId = { "unused-device" },
        ).shouldBeInstanceOf<PairingPayloadReceiver.Plan.ClusterMismatch>()

        mismatch.localClusterId shouldBe "cluster-local"
        mismatch.payloadClusterId shouldBe "cluster-peer"
    }

    test("PairingPayloadReceiver folds in sender clock without lowering local state") {
        val payload = PairingPayloadGenerator.generate(
            displayName = "Phone",
            syncChannelId = SyncChannel.ManualExport.channelId,
            clusterId = "cluster-1",
            deviceId = "peer-device",
            senderClock = 500L,
        )

        val accepted = PairingPayloadReceiver.plan(
            payload = payload,
            localClusterId = "cluster-1",
            localDeviceId = "local-device",
            pairedDevicesJson = "[]",
            previousLocalState = PersonalDictionaryCrdt(deviceId = "local-device", clock = 700L),
            pairedAtMillis = 123L,
            newDeviceId = { "unused-device" },
        ).shouldBeInstanceOf<PairingPayloadReceiver.Plan.Accepted>()

        accepted.foldedLocalState.clock shouldBe 700L
    }

    test("SyncQrCode encodes pairing JSON into a square matrix") {
        val payload = PairingPayloadGenerator.generate(
            displayName = "Phone",
            syncChannelId = SyncChannel.Disabled.channelId,
            clusterId = "cluster-1",
            deviceId = "device-1",
            senderClock = 42L,
        ).serializeToString()

        val matrix = SyncQrCode.encode(payload, size = 37)

        (matrix.size >= 37) shouldBe true
        matrix.cells.size shouldBe matrix.size * matrix.size
        matrix.cells.any { it } shouldBe true
        matrix.cells.any { !it } shouldBe true
    }

    test("SyncQrCode rejects unusable input") {
        shouldThrow<IllegalArgumentException> { SyncQrCode.encode("") }
        shouldThrow<IllegalArgumentException> { SyncQrCode.encode("{}", size = 20) }
    }

})
