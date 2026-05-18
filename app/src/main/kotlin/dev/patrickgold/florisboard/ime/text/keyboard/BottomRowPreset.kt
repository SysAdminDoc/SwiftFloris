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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.AbstractKeyData
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.popup.PopupSet
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BottomRowPreset(
    val keys: List<BottomRowKey> = DefaultKeys,
) {
    fun normalized(): BottomRowPreset {
        val deduped = keys.distinct().filter { it != BottomRowKey.SPACE }
        val safeKeys = buildList {
            val spaceIndex = keys.indexOf(BottomRowKey.SPACE)
            if (spaceIndex < 0) {
                addAll(DefaultKeys)
            } else {
                val beforeSpace = deduped.filter { keys.indexOf(it) in 0 until spaceIndex }
                val afterSpace = deduped.filter { keys.indexOf(it) > spaceIndex }
                addAll(beforeSpace)
                add(BottomRowKey.SPACE)
                addAll(afterSpace)
            }
        }
        return copy(keys = safeKeys.take(MaxKeys).ifEmpty { DefaultKeys })
    }

    fun contains(key: BottomRowKey): Boolean {
        return normalized().keys.contains(key)
    }

    fun toJson(): String {
        return JsonConfig.encodeToString(normalized())
    }

    fun toTextKeyDataRow(): List<TextKeyData> {
        val normalizedKeys = normalized().keys
        val hasDedicatedVoice = BottomRowKey.VOICE_INPUT in normalizedKeys
        return normalizedKeys.map { it.toTextKeyData(hasDedicatedVoice) }
    }

    companion object {
        const val AutomaticPreferenceValue = "automatic"

        private const val MaxKeys = 9

        val DefaultKeys = listOf(
            BottomRowKey.VIEW_SYMBOLS,
            BottomRowKey.EMOJI,
            BottomRowKey.COMMA,
            BottomRowKey.SPACE,
            BottomRowKey.PERIOD,
            BottomRowKey.ENTER,
        )

        val SwiftKey = BottomRowPreset(DefaultKeys)
        val Language = BottomRowPreset(
            listOf(
                BottomRowKey.VIEW_SYMBOLS,
                BottomRowKey.EMOJI,
                BottomRowKey.LANGUAGE_PICKER,
                BottomRowKey.COMMA,
                BottomRowKey.SPACE,
                BottomRowKey.PERIOD,
                BottomRowKey.ENTER,
            )
        )
        val Voice = BottomRowPreset(
            listOf(
                BottomRowKey.VIEW_SYMBOLS,
                BottomRowKey.EMOJI,
                BottomRowKey.COMMA,
                BottomRowKey.VOICE_INPUT,
                BottomRowKey.SPACE,
                BottomRowKey.PERIOD,
                BottomRowKey.ENTER,
            )
        )
        val Settings = BottomRowPreset(
            listOf(
                BottomRowKey.VIEW_SYMBOLS,
                BottomRowKey.EMOJI,
                BottomRowKey.COMMA,
                BottomRowKey.SPACE,
                BottomRowKey.PERIOD,
                BottomRowKey.SETTINGS,
                BottomRowKey.ENTER,
            )
        )
        val Minimal = BottomRowPreset(
            listOf(
                BottomRowKey.VIEW_SYMBOLS,
                BottomRowKey.SPACE,
                BottomRowKey.ENTER,
            )
        )
        /** ROADMAP §7 Next-8.1a — Programmer bottom row. Surfaces Tab + Esc
         *  + symbols / arrow / brace key cluster directly in the main
         *  letter-keyboard view, so terminal / IDE users on Termux /
         *  JuiceSSH / Acode don't have to keep flipping to the symbol
         *  layer for the keys they hit most. Complements Next-8.1 +
         *  Next-8.2's CODE smartbar profile (which only adds smartbar slots,
         *  not in-keyboard keys). User selects this preset under
         *  Settings → Keyboard → Bottom-row preset → "Programmer". */
        val Programmer = BottomRowPreset(
            listOf(
                BottomRowKey.VIEW_SYMBOLS,
                BottomRowKey.TAB,
                BottomRowKey.ESCAPE,
                BottomRowKey.SPACE,
                BottomRowKey.PERIOD,
                BottomRowKey.SLASH,
                BottomRowKey.ENTER,
            )
        )

        /** docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §C2 — dedicated arrow-keys
         *  bottom row. SwiftKey ships a "Modes → Arrow keys" affordance
         *  that swaps the standard bottom row for ← ↑ ↓ → so cursor
         *  navigation doesn't need the space-bar trackpad gesture or a
         *  hardware-keyboard handoff. The space bar shrinks but stays
         *  present (the user still needs to type spaces between
         *  navigation hops). Period stays for sentence punctuation; the
         *  symbols-view toggle is dropped to fit the four arrows. */
        val Navigation = BottomRowPreset(
            listOf(
                BottomRowKey.ARROW_LEFT,
                BottomRowKey.ARROW_UP,
                BottomRowKey.SPACE,
                BottomRowKey.ARROW_DOWN,
                BottomRowKey.ARROW_RIGHT,
                BottomRowKey.ENTER,
            )
        )

        val Presets = listOf(SwiftKey, Language, Voice, Settings, Minimal, Programmer, Navigation)

        fun fromJsonOverride(rawValue: String): BottomRowPreset? {
            return when {
                rawValue.isBlank() || rawValue == AutomaticPreferenceValue -> null
                else -> runCatching {
                    JsonConfig.decodeFromString<BottomRowPreset>(rawValue).normalized()
                }.getOrDefault(SwiftKey)
            }
        }

        private val JsonConfig = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
enum class BottomRowKey {
    VIEW_SYMBOLS,
    EMOJI,
    LANGUAGE_PICKER,
    COMMA,
    VOICE_INPUT,
    SETTINGS,
    SPACE,
    PERIOD,
    ENTER,

    // ROADMAP §7 Next-8.1a — Programmer-mode keys for the Programmer
    // bottom-row preset. TAB / ESCAPE are character keys (codes 9 / 27)
    // that commit literal `\t` / ESC to the focused editor; SLASH is
    // the bracket / brace launcher with a popup that exposes the full
    // code-symbol cluster on long-press.
    TAB,
    ESCAPE,
    SLASH,

    // docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §C2 — arrow keys for the
    // Navigation bottom-row preset. Reuse the existing TextKeyData
    // arrow constants so the runtime cursor-movement path is already
    // wired (KeyboardManager.onInputKeyUp dispatches ARROW_*).
    ARROW_LEFT,
    ARROW_UP,
    ARROW_DOWN,
    ARROW_RIGHT;

    internal fun toTextKeyData(hasDedicatedVoice: Boolean): TextKeyData {
        return when (this) {
            VIEW_SYMBOLS -> TextKeyData.VIEW_SYMBOLS
            EMOJI -> TextKeyData.IME_UI_MODE_MEDIA
            LANGUAGE_PICKER -> TextKeyData.SHOW_SUBTYPE_PICKER
            COMMA -> TextKeyData(
                code = 44,
                label = ",",
                groupId = KeyData.GROUP_LEFT,
                popup = if (hasDedicatedVoice) null else PopupSet<AbstractKeyData>(main = TextKeyData.VOICE_INPUT),
            )
            VOICE_INPUT -> TextKeyData.VOICE_INPUT
            SETTINGS -> TextKeyData.SETTINGS
            SPACE -> TextKeyData.SPACE
            PERIOD -> TextKeyData(
                code = 46,
                label = ".",
                groupId = KeyData.GROUP_RIGHT,
                popup = PopupSet<AbstractKeyData>(
                    main = TextKeyData(code = 33, label = "!"),
                    relevant = listOf(TextKeyData(code = 63, label = "?")),
                ),
            )
            ENTER -> TextKeyData(
                type = KeyType.ENTER_EDITING,
                code = KeyCode.ENTER,
                label = "enter",
                groupId = KeyData.GROUP_ENTER,
            )
            TAB -> TextKeyData.TAB
            ESCAPE -> TextKeyData.ESCAPE
            SLASH -> TextKeyData(
                code = 47,
                label = "/",
                popup = PopupSet<AbstractKeyData>(
                    main = TextKeyData(code = 92, label = "\\"),
                    relevant = listOf(
                        TextKeyData(code = 123, label = "{"),
                        TextKeyData(code = 125, label = "}"),
                        TextKeyData(code = 91, label = "["),
                        TextKeyData(code = 93, label = "]"),
                        TextKeyData(code = 40, label = "("),
                        TextKeyData(code = 41, label = ")"),
                        TextKeyData(code = 60, label = "<"),
                        TextKeyData(code = 62, label = ">"),
                        TextKeyData(code = 124, label = "|"),
                        TextKeyData(code = 96, label = "`"),
                        TextKeyData(code = 126, label = "~"),
                    ),
                ),
            )
            ARROW_LEFT -> TextKeyData.ARROW_LEFT
            ARROW_RIGHT -> TextKeyData.ARROW_RIGHT
            ARROW_UP -> TextKeyData.ARROW_UP
            ARROW_DOWN -> TextKeyData.ARROW_DOWN
        }
    }
}
