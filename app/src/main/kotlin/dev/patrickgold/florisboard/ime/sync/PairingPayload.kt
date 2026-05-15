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

/**
 * ROADMAP §7 Next-5.2 — first-launch QR-pair payload shape.
 *
 * When a user wants to join an existing SwiftFloris device cluster, the
 * existing device renders a QR code carrying this payload. The new
 * device scans it, learns the cluster's shared-folder location and
 * recipient pubkeys, generates its own Curve25519 keypair, sends its
 * pubkey back (via the same QR mechanism in reverse), and from that
 * point on wraps every CRDT delta with a libsodium sealed-box keyed to
 * the cluster's pubkey set.
 *
 * The payload is JSON for human-debuggability and so future versions
 * can add fields without breaking older scanners (they ignore unknown
 * keys via [Json.ignoreUnknownKeys] in the parser). The QR-encoded form
 * is the raw JSON; for very small QR sizes a callsite may opt to base85
 * pack it, but the on-the-wire shape stays JSON.
 *
 * Curve25519 pubkeys are 32 raw bytes encoded as 64-char lowercase hex.
 * libsodium implementation lands in Next-5.2a alongside the actual
 * sealed-box / KDF wiring. This scaffold pins the payload shape so the
 * QR producer + scanner can ship independently from the crypto bring-up.
 */
@Serializable
data class PairingPayload(
    /** Schema version of this payload. Currently 1. */
    val schema: Int,
    /** Stable cluster identifier (UUID v4 chosen at cluster bootstrap). */
    val clusterId: String,
    /** Sender's stable device id (lets the receiver address the reply). */
    val deviceId: String,
    /** 32-byte Curve25519 public key, lowercase-hex (length 64). */
    val pubkeyHex: String,
    /** Display name of the sending device (e.g. `"Matt's Pixel 9"`). */
    val displayName: String,
    /** User-chosen sync-channel id. Free-form; both devices must agree
     *  out-of-band. Examples: `"swiftfloris:syncthing:home"`,
     *  `"swiftfloris:nextcloud:Apps/SwiftFloris/dict"`. */
    val syncChannelId: String,
    /** Lamport clock value at the moment the QR was generated. The
     *  receiver folds this into its own clock so a freshly-paired
     *  device immediately sees write ordering aligned with the cluster. */
    val senderClock: Long,
) {
    init {
        require(schema >= 1) { "schema must be >= 1" }
        require(clusterId.isNotBlank()) { "clusterId must not be blank" }
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(pubkeyHex.matches(Regex("^[0-9a-f]{64}$"))) {
            "pubkeyHex must be 64 chars of lowercase hex (Curve25519 pubkey, 32 bytes)"
        }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(syncChannelId.isNotBlank()) { "syncChannelId must not be blank" }
        require(senderClock >= 0) { "senderClock must be non-negative" }
    }

    fun serializeToString(): String = JsonConfig.encodeToString(serializer(), this)

    companion object {
        const val SUPPORTED_SCHEMA: Int = 1

        private val JsonConfig = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        /** Decode a scanned-QR payload back into a [PairingPayload].
         *  Returns null on invalid JSON or validation failure. */
        fun parse(rawJson: String): PairingPayload? {
            if (rawJson.isBlank()) return null
            return runCatching { JsonConfig.decodeFromString(serializer(), rawJson) }
                .getOrNull()
        }
    }
}
