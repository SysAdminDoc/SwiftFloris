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

package dev.patrickgold.florisboard.ime.tasker

/**
 * ROADMAP §7 L11.1 — Tasker intent endpoint contract.
 *
 * Tasker is the universal Android automation app; "broadcast intent →
 * action" is its core extension mechanism. Exposing a few well-defined
 * intents lets users wire IME-driven actions into existing Tasker
 * scenes (e.g. "when Tasker says I'm at home, switch the IME to
 * Dvorak"). This contract pins the intent surface so the receiver
 * implementation can land independently from the wiring.
 *
 * The IME registers `BroadcastReceiver`s for these actions inside
 * `AndroidManifest.xml` (Next-L11.1a — see the corresponding `<receiver>`
 * block once the receiver lives in `:app`). The actions are
 * **signature-protected** by the same `permission.REGISTER_ADDON`
 * surface the addon framework uses (no random app can send these);
 * Tasker users grant the IME the relevant permission once per device.
 *
 * Every action carries a small fixed extras schema documented inline
 * to make the surface predictable to script authors.
 */
object TaskerIntentContract {

    /**
     * Permission a Tasker-class sender must hold to send any of the
     * intents in this contract. Same signature-protected permission
     * the addon framework uses (Next-10.1).
     */
    const val PERMISSION_TRIGGER: String =
        "dev.patrickgold.florisboard.permission.REGISTER_ADDON"

    /** Insert literal text at the cursor of the focused editor. */
    object InsertText {
        const val ACTION: String = "swiftfloris.action.INSERT_TEXT"

        /** Extras key: required UTF-8 text to insert. */
        const val EXTRA_TEXT: String = "text"
        /** Extras key: optional boolean — append a trailing space after the insert. */
        const val EXTRA_APPEND_SPACE: String = "appendSpace"
    }

    /** Insert the current clipboard primary item at the cursor. */
    object InsertClipboard {
        const val ACTION: String = "swiftfloris.action.INSERT_CLIP"
    }

    /** Switch the active subtype layout. */
    object SwitchLayout {
        const val ACTION: String = "swiftfloris.action.SWITCH_LAYOUT"

        /** Extras key: required layout id (e.g. `dvorak`, `colemak_dh`). */
        const val EXTRA_LAYOUT_ID: String = "layoutId"
    }

    /** Trigger the voice-input session. */
    object TriggerVoice {
        const val ACTION: String = "swiftfloris.action.TRIGGER_VOICE"

        /** Extras key: optional command mode flag (`"dictation"` | `"command"`). */
        const val EXTRA_MODE: String = "mode"
    }

    /**
     * Validate an incoming Tasker intent: confirms the action is a
     * known SwiftFloris action, the extras are well-formed for that
     * action, and the values fall within the per-action size cap. The
     * receiver should reject any intent that fails this check.
     */
    fun validate(action: String, extras: Map<String, Any?>): ValidationResult {
        return when (action) {
            InsertText.ACTION -> {
                val unexpected = rejectUnexpectedExtras(
                    extras,
                    allowed = setOf(InsertText.EXTRA_TEXT, InsertText.EXTRA_APPEND_SPACE),
                )
                if (unexpected != null) return unexpected
                val text = extras[InsertText.EXTRA_TEXT] as? String
                val appendSpace = extras[InsertText.EXTRA_APPEND_SPACE]
                when {
                    text == null -> ValidationResult.Reject("missing required EXTRA_TEXT")
                    text.isEmpty() -> ValidationResult.Reject("EXTRA_TEXT must not be empty")
                    text.length > MAX_INSERT_LENGTH -> ValidationResult.Reject(
                        "EXTRA_TEXT exceeds $MAX_INSERT_LENGTH chars",
                    )
                    appendSpace != null && appendSpace !is Boolean -> ValidationResult.Reject(
                        "EXTRA_APPEND_SPACE must be boolean when present",
                    )
                    else -> ValidationResult.Accept
                }
            }
            InsertClipboard.ACTION -> {
                rejectUnexpectedExtras(extras, allowed = emptySet()) ?: ValidationResult.Accept
            }
            SwitchLayout.ACTION -> {
                val unexpected = rejectUnexpectedExtras(
                    extras,
                    allowed = setOf(SwitchLayout.EXTRA_LAYOUT_ID),
                )
                if (unexpected != null) return unexpected
                val layoutId = extras[SwitchLayout.EXTRA_LAYOUT_ID] as? String
                when {
                    layoutId == null -> ValidationResult.Reject("missing required EXTRA_LAYOUT_ID")
                    layoutId.isBlank() -> ValidationResult.Reject("EXTRA_LAYOUT_ID must not be blank")
                    !layoutId.matches(LAYOUT_ID_REGEX) -> ValidationResult.Reject(
                        "EXTRA_LAYOUT_ID must match $LAYOUT_ID_REGEX",
                    )
                    else -> ValidationResult.Accept
                }
            }
            TriggerVoice.ACTION -> {
                val unexpected = rejectUnexpectedExtras(
                    extras,
                    allowed = setOf(TriggerVoice.EXTRA_MODE),
                )
                if (unexpected != null) return unexpected
                val rawMode = extras[TriggerVoice.EXTRA_MODE]
                when {
                    rawMode == null -> ValidationResult.Accept
                    rawMode !is String -> ValidationResult.Reject("EXTRA_MODE must be a string")
                    rawMode !in VOICE_MODES -> ValidationResult.Reject(
                        "EXTRA_MODE must be 'dictation' or 'command'",
                    )
                    else -> ValidationResult.Accept
                }
            }
            else -> ValidationResult.Reject("unknown SwiftFloris Tasker action: $action")
        }
    }

    /** Hard cap on inserted text length to prevent flooding the editor. */
    const val MAX_INSERT_LENGTH: Int = 4096

    private val LAYOUT_ID_REGEX = Regex("^[a-z0-9_]{1,32}$")
    private val VOICE_MODES = setOf("dictation", "command")

    private fun rejectUnexpectedExtras(
        extras: Map<String, Any?>,
        allowed: Set<String>,
    ): ValidationResult.Reject? {
        val unexpected = extras.keys - allowed
        if (unexpected.isEmpty()) return null
        return ValidationResult.Reject(
            "unexpected Tasker extras: ${unexpected.sorted().joinToString()}",
        )
    }
}

sealed class ValidationResult {
    object Accept : ValidationResult()
    data class Reject(val reason: String) : ValidationResult()
}
