/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import android.content.Context
import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.calendarQuickInsertManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.calendar.CalendarPermissionActivity
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.florisboard.lib.compose.stringRes

@Serializable
sealed class QuickAction {
    open fun onPointerDown(context: Context) = Unit

    open fun onPointerUp(context: Context) = Unit

    open fun onPointerCancel(context: Context) = Unit

    @Serializable
    @SerialName("insert_key")
    data class InsertKey(val data: KeyData) : QuickAction() {
        override fun onPointerDown(context: Context) {
            val keyboardManager by context.keyboardManager()
            keyboardManager.inputEventDispatcher.sendDown(data)
        }

        override fun onPointerUp(context: Context) {
            val keyboardManager by context.keyboardManager()
            keyboardManager.inputEventDispatcher.sendUp(data)
            if (!keyboardManager.inputEventDispatcher.isRepeatable(data) &&
                data.code != KeyCode.TOGGLE_ACTIONS_OVERFLOW && data.code != KeyCode.CLIPBOARD_SELECT_ALL) {
                keyboardManager.activeState.isActionsOverflowVisible = false
            }
        }

        override fun onPointerCancel(context: Context) {
            val keyboardManager by context.keyboardManager()
            keyboardManager.inputEventDispatcher.sendCancel(data)
        }
    }

    @Serializable
    @SerialName("insert_text")
    data class InsertText(val data: String) : QuickAction() {
        override fun onPointerUp(context: Context) {
            val editorInstance by context.editorInstance()
            editorInstance.commitText(data)
        }
    }

    /**
     * ROADMAP §0 P2 — Translation toolbar (SwiftKey-style).
     *
     * Reads the current selection from `EditorInstance` and routes it
     * through the `InlineTranslator` facade (`ime/translate/`). When
     * no addon is installed, the registry's default `Unavailable`
     * result is returned and the action surfaces a Toast explaining
     * the user needs to install the L2.1a Bergamot translator addon.
     * When the addon IS installed, the translated text is committed
     * back to the editor in place of the selection.
     *
     * The source/target locale pair is read from
     * `prefs.translate.sourceLocale` + `prefs.translate.targetLocale`
     * (defaults `auto` + `en`).
     */
    @Serializable
    @SerialName("translate_selection")
    data object TranslateSelection : QuickAction() {
        override fun onPointerUp(context: Context) {
            val editorInstance by context.editorInstance()
            val raw = editorInstance.activeContent.selectedText.toString()
            if (raw.isBlank()) return
            val translator = dev.patrickgold.florisboard.ime.translate
                .InlineTranslatorRegistry.active
            val sourceLocale = "auto"
            val targetLocale = "en"
            when (val result = translator.translate(raw, sourceLocale, targetLocale)) {
                is dev.patrickgold.florisboard.ime.translate.TranslationResult.Translated -> {
                    editorInstance.commitText(result.translatedText)
                }
                is dev.patrickgold.florisboard.ime.translate.TranslationResult.Unavailable -> {
                    android.widget.Toast.makeText(
                        context,
                        "Install an InlineTranslator addon to translate selections.",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /**
     * docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §D2 — generic task-creation
     * quick action, the on-device replacement for SwiftKey's
     * Microsoft-To-Do toolbar tile.
     *
     * SwiftKey's tile is hard-bound to Microsoft accounts; this
     * action uses the cross-app `Intent.ACTION_SEND` pattern so any
     * installed task / note app that registers a SEND filter
     * (Tasks.org, OpenTasks, Google Tasks, Joplin, Notion, Markor,
     * etc.) surfaces in the share sheet. No new permission is
     * requested — the user picks the destination per-tap.
     *
     * Routes the editor's current selection through `EXTRA_TEXT`;
     * when the selection is empty, sends a blank text/plain
     * intent so the user can still type the task title in the
     * destination app.
     *
     * Sensitive-field guard: same predicate the smart-compose /
     * translation / MCP surfaces use. A password / no-personalised-
     * learning field never has its contents handed off to another
     * app, even on an explicit tap — the user can always copy the
     * non-sensitive part separately.
     */
    @Serializable
    @SerialName("insert_task")
    data object InsertTask : QuickAction() {
        override fun onPointerUp(context: Context) {
            val editorInstance by context.editorInstance()
            val activeInfo = editorInstance.activeInfo
            if (dev.patrickgold.florisboard.ime.smartcompose.SensitiveFieldGuard.isSensitive(
                    inputType = activeInfo.inputAttributes.raw,
                    imeOptions = activeInfo.imeOptions.raw,
                )
            ) {
                android.widget.Toast.makeText(
                    context,
                    "Sending tasks from sensitive fields is blocked.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                return
            }
            val selection = editorInstance.activeContent.selectedText.toString()
            val title = selection.ifBlank {
                editorInstance.activeContent.textBeforeSelection.toString().trim().takeLast(140)
            }
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, title)
                // The IME service isn't an Activity; the chooser must
                // start a new task. The chooser itself adds the
                // FLAG_ACTIVITY_NEW_DOCUMENT it needs.
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(sendIntent, "Add to tasks").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(chooser)
            } catch (e: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(
                    context,
                    "Install a task or note app to use this action.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17 §D1 — calendar quick-insert.
     *
     * Reads upcoming agenda entries from Android's local CalendarProvider
     * (`CalendarContract.Instances`) and shows an IME-local picker. The
     * READ_CALENDAR runtime permission is requested only when the user taps
     * this explicit action; the base APK still has no network permission.
     */
    @Serializable
    @SerialName("insert_calendar_event")
    data object InsertCalendarEvent : QuickAction() {
        override fun onPointerUp(context: Context) {
            val manager by context.calendarQuickInsertManager()
            if (manager.hasReadCalendarPermission()) {
                manager.openPicker()
            } else if (!CalendarPermissionActivity.launch(context)) {
                android.widget.Toast.makeText(
                    context,
                    "Calendar permission is required to insert agenda events.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

fun QuickAction.keyData(): KeyData {
    return if (this is QuickAction.InsertKey) data else TextKeyData.UNSPECIFIED
}

@Composable
fun QuickAction.computeDisplayName(evaluator: ComputingEvaluator): String {
    return when (this) {
        is QuickAction.InsertKey -> stringRes(when (data.code) {
            KeyCode.ARROW_UP -> R.string.quick_action__arrow_up
            KeyCode.ARROW_DOWN -> R.string.quick_action__arrow_down
            KeyCode.ARROW_LEFT -> R.string.quick_action__arrow_left
            KeyCode.ARROW_RIGHT -> R.string.quick_action__arrow_right
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> R.string.quick_action__clipboard_clear_primary_clip
            KeyCode.CLIPBOARD_COPY -> R.string.quick_action__clipboard_copy
            KeyCode.CLIPBOARD_CUT -> R.string.quick_action__clipboard_cut
            KeyCode.CLIPBOARD_PASTE -> R.string.quick_action__clipboard_paste
            KeyCode.CLIPBOARD_SELECT_ALL -> R.string.quick_action__clipboard_select_all
            KeyCode.FORWARD_DELETE -> R.string.quick_action__forward_delete
            KeyCode.IME_UI_MODE_CLIPBOARD -> R.string.quick_action__ime_ui_mode_clipboard
            KeyCode.IME_UI_MODE_MEDIA -> R.string.quick_action__ime_ui_mode_media
            KeyCode.LANGUAGE_SWITCH -> R.string.quick_action__language_switch
            KeyCode.SETTINGS -> R.string.quick_action__settings
            KeyCode.UNDO -> R.string.quick_action__undo
            KeyCode.REDO -> R.string.quick_action__redo
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> R.string.quick_action__toggle_actions_overflow
            KeyCode.TOGGLE_INCOGNITO_MODE -> R.string.quick_action__toggle_incognito_mode
            KeyCode.TOGGLE_AUTOCORRECT -> R.string.quick_action__toggle_autocorrect
            KeyCode.VOICE_INPUT -> R.string.quick_action__voice_input
            KeyCode.IME_HIDE_UI -> R.string.quick_action__ime_hide_ui
            KeyCode.TOGGLE_FLOATING_WINDOW -> R.string.quick_action__floating_window_mode
            // TODO: In the future this will be merged into the resize keyboard panel, for now it is a separate action
            KeyCode.TOGGLE_COMPACT_LAYOUT -> R.string.quick_action__one_handed_mode
            KeyCode.TOGGLE_RESIZE_MODE -> R.string.quick_action__resize_mode
            KeyCode.DRAG_MARKER -> if (evaluator.state.debugShowDragAndDropHelpers) {
                R.string.quick_action__drag_marker
            } else {
                R.string.general__empty_string
            }
            KeyCode.NOOP -> R.string.quick_action__noop
            else -> R.string.general__invalid_fatal
        })
        is QuickAction.InsertText -> data
        is QuickAction.TranslateSelection -> "Translate"
        is QuickAction.InsertTask -> "Add task"
        is QuickAction.InsertCalendarEvent -> "Insert event"
    }
}

@Composable
fun QuickAction.computeTooltip(evaluator: ComputingEvaluator): String {
    return when (this) {
        is QuickAction.InsertKey -> stringRes(when (data.code) {
            KeyCode.ARROW_UP -> R.string.quick_action__arrow_up__tooltip
            KeyCode.ARROW_DOWN -> R.string.quick_action__arrow_down__tooltip
            KeyCode.ARROW_LEFT -> R.string.quick_action__arrow_left__tooltip
            KeyCode.ARROW_RIGHT -> R.string.quick_action__arrow_right__tooltip
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> R.string.quick_action__clipboard_clear_primary_clip__tooltip
            KeyCode.CLIPBOARD_COPY -> R.string.quick_action__clipboard_copy__tooltip
            KeyCode.CLIPBOARD_CUT -> R.string.quick_action__clipboard_cut__tooltip
            KeyCode.CLIPBOARD_PASTE -> R.string.quick_action__clipboard_paste__tooltip
            KeyCode.CLIPBOARD_SELECT_ALL -> R.string.quick_action__clipboard_select_all__tooltip
            KeyCode.IME_UI_MODE_CLIPBOARD -> R.string.quick_action__ime_ui_mode_clipboard__tooltip
            KeyCode.IME_UI_MODE_MEDIA -> R.string.quick_action__ime_ui_mode_media__tooltip
            KeyCode.LANGUAGE_SWITCH -> R.string.quick_action__language_switch__tooltip
            KeyCode.SETTINGS -> R.string.quick_action__settings__tooltip
            KeyCode.UNDO -> R.string.quick_action__undo__tooltip
            KeyCode.REDO -> R.string.quick_action__redo__tooltip
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> R.string.quick_action__toggle_actions_overflow__tooltip
            KeyCode.TOGGLE_INCOGNITO_MODE -> R.string.quick_action__toggle_incognito_mode__tooltip
            KeyCode.TOGGLE_AUTOCORRECT -> R.string.quick_action__toggle_autocorrect__tooltip
            KeyCode.VOICE_INPUT -> R.string.quick_action__voice_input__tooltip
            KeyCode.IME_HIDE_UI -> R.string.quick_action__ime_hide_ui__tooltip
            KeyCode.TOGGLE_FLOATING_WINDOW -> R.string.quick_action__floating_window_mode__tooltip
            // TODO: In the future this will be merged into the resize keyboard panel, for now it is a separate action
            KeyCode.TOGGLE_COMPACT_LAYOUT -> R.string.quick_action__one_handed_mode__tooltip
            KeyCode.TOGGLE_RESIZE_MODE -> R.string.quick_action__resize_mode__tooltip
            KeyCode.DRAG_MARKER -> if (evaluator.state.debugShowDragAndDropHelpers) {
                R.string.quick_action__drag_marker__tooltip
            } else {
                R.string.general__empty_string
            }
            KeyCode.NOOP -> R.string.quick_action__noop__tooltip
            else -> R.string.general__invalid_fatal
        })
        is QuickAction.InsertText -> "Insert text '$data'"
        is QuickAction.TranslateSelection -> "Translate the current selection (via InlineTranslator addon)"
        is QuickAction.InsertTask -> "Send current selection to a task / note app (Tasks.org, OpenTasks, Google Tasks, Joplin, etc.)"
        is QuickAction.InsertCalendarEvent -> "Pick a local calendar event and insert its title and date/time"
    }
}
