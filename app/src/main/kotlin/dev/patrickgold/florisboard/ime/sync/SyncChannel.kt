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

/**
 * ROADMAP §7 Next-5.3 — "Bring your own sync channel".
 *
 * The CRDT merge (Next-5.1) is local and the QR pairing payload
 * (Next-5.2) carries a free-form `syncChannelId` string that the
 * transport-layer code consumes. Next-5.3 makes that channel
 * user-selectable: Syncthing for default, but Nextcloud / Resilio /
 * Dropbox-via-FolderSync / even Email-this-blob are all equally
 * acceptable because the IME never touches the network itself —
 * it just reads / writes JSON files in a folder the user has handed
 * over to whatever sync app they prefer.
 *
 * This scaffold pins:
 *  - [SyncChannel] — sealed class enumerating the channels the
 *    Settings → Sync screen can offer.
 *  - [SyncChannelKind] — the discriminator string format every
 *    channel uses inside [PairingPayload.syncChannelId].
 *  - [SyncChannelDescriptor] — display metadata for the Settings UI.
 *
 * The Settings UI ships in Next-5.3a once the corresponding Compose
 * screen is wired up; the data model here is sufficient for the
 * channel-identifier validators to pin contract today.
 */
sealed class SyncChannel {
    abstract val descriptor: SyncChannelDescriptor
    abstract val channelId: String

    /**
     * Default channel: a Syncthing-shared folder. `folderName` is the
     * Syncthing folder id the user has configured ("swiftfloris-sync").
     */
    @Serializable
    data class Syncthing(val folderName: String) : SyncChannel() {
        override val descriptor get() = SyncChannelDescriptor(
            kind = SyncChannelKind.SYNCTHING,
            displayName = "Syncthing",
            briefDescription = "Direct device-to-device sync via the Syncthing app.",
        )
        override val channelId: String get() = "swiftfloris:syncthing:$folderName"
        init {
            require(folderName.isNotBlank()) { "folderName must not be blank" }
        }
    }

    /**
     * Generic local-folder channel: the user picks any directory and
     * lets a sync app (Nextcloud, Resilio, Dropbox via FolderSync) mirror
     * it. The IME just reads / writes JSON in [absolutePath], which may be
     * a POSIX-style absolute path or an Android SAF content URI.
     */
    @Serializable
    data class LocalFolder(val absolutePath: String, val displayLabel: String) : SyncChannel() {
        override val descriptor get() = SyncChannelDescriptor(
            kind = SyncChannelKind.LOCAL_FOLDER,
            displayName = displayLabel.ifBlank { "Local folder" },
            briefDescription = "Read / write CRDT files in a folder your chosen sync app mirrors.",
        )
        override val channelId: String get() = "swiftfloris:folder:$absolutePath"
        init {
            require(absolutePath.isNotBlank()) { "absolutePath must not be blank" }
            require(isSupportedLocalFolderLocation(absolutePath)) {
                "absolutePath must be an absolute filesystem path or SAF content URI"
            }
        }
    }

    /**
     * Manual-export channel: the IME writes a single CRDT blob to a
     * file the user shares via the share-sheet (email, Signal, etc.).
     * No background sync — the user is the transport. Useful for
     * single-pair "set up my new phone today" scenarios.
     */
    @Serializable
    data object ManualExport : SyncChannel() {
        override val descriptor get() = SyncChannelDescriptor(
            kind = SyncChannelKind.MANUAL_EXPORT,
            displayName = "Manual export",
            briefDescription = "Share a CRDT snapshot file once via your share sheet.",
        )
        override val channelId: String = "swiftfloris:manual-export"
    }

    /**
     * Disabled — sync is off; the IME stays purely local. Listed in
     * the channel selector for clarity (the implicit "off" state).
     */
    @Serializable
    data object Disabled : SyncChannel() {
        override val descriptor get() = SyncChannelDescriptor(
            kind = SyncChannelKind.DISABLED,
            displayName = "Sync off",
            briefDescription = "Personal dictionary stays on this device only.",
        )
        override val channelId: String = "swiftfloris:disabled"
    }

    companion object {
        /**
         * Parse [channelId] (as produced by [PairingPayload.syncChannelId])
         * back into a typed [SyncChannel]. Returns [Disabled] for
         * unrecognised channels so a corrupt config never silently
         * leaks into a real sync channel.
         */
        fun parse(channelId: String): SyncChannel {
            if (channelId.isBlank()) return Disabled
            val parts = channelId.split(':', limit = 3)
            if (parts.size < 2 || parts[0] != "swiftfloris") return Disabled
            return when (parts[1]) {
                "syncthing" -> {
                    val folder = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return Disabled
                    Syncthing(folder)
                }
                "folder" -> {
                    val path = parts.getOrNull(2)
                        ?.takeIf { isSupportedLocalFolderLocation(it) }
                        ?: return Disabled
                    LocalFolder(path, displayLabel = "Local folder")
                }
                "manual-export" -> ManualExport
                "disabled" -> Disabled
                else -> Disabled
            }
        }
    }
}

private fun isSupportedLocalFolderLocation(value: String): Boolean {
    return value.startsWith("/") || value.startsWith("content://")
}

@Serializable
enum class SyncChannelKind {
    SYNCTHING, LOCAL_FOLDER, MANUAL_EXPORT, DISABLED;
}

data class SyncChannelDescriptor(
    val kind: SyncChannelKind,
    val displayName: String,
    val briefDescription: String,
)
