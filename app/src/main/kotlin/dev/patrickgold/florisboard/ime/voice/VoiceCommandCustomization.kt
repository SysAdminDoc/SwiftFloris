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

package dev.patrickgold.florisboard.ime.voice

import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VoiceCommandCustomCommand(
    val id: String,
    val phrase: String,
    val action: VoiceCommandAction,
    val enabled: Boolean = true,
) {
    fun asDefinitionOrNull(): VoiceCommandDefinition? {
        val normalizedPhrase = phrase.trim().replace(WhitespaceRegex, " ")
        val normalizedId = id.trim()
        if (!enabled || normalizedId.isEmpty() || normalizedPhrase.isEmpty()) {
            return null
        }
        return VoiceCommandDefinition(
            action = action,
            canonicalPhrase = normalizedPhrase,
        )
    }

    private companion object {
        val WhitespaceRegex = "\\s+".toRegex()
    }
}

@Serializable
data class VoiceCommandCustomCommands(
    val commands: List<VoiceCommandCustomCommand> = emptyList(),
) {
    fun enabledDefinitions(): List<VoiceCommandDefinition> {
        return commands.mapNotNull { it.asDefinitionOrNull() }
    }

    fun upsert(command: VoiceCommandCustomCommand): VoiceCommandCustomCommands {
        val filtered = commands.filterNot { it.id == command.id }
        return copy(commands = filtered + command)
    }

    fun remove(commandId: String): VoiceCommandCustomCommands {
        return copy(commands = commands.filterNot { it.id == commandId })
    }

    object Serializer : PreferenceSerializer<VoiceCommandCustomCommands> {
        override fun serialize(value: VoiceCommandCustomCommands): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): VoiceCommandCustomCommands {
            return try {
                Json.decodeFromString(value)
            } catch (e: Exception) {
                flogError { "Failed to deserialize VoiceCommandCustomCommands: $e" }
                Empty
            }
        }
    }

    companion object {
        val Empty = VoiceCommandCustomCommands()
    }
}
