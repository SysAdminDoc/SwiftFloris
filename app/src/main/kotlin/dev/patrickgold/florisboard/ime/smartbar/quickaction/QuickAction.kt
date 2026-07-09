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
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.calendarQuickInsertManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.calendar.CalendarPermissionActivity
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.translate.InlineTranslatorRegistry
import dev.patrickgold.florisboard.ime.translate.TranslationLanguagePackManager
import dev.patrickgold.florisboard.ime.translate.TranslationRouter
import dev.patrickgold.florisboard.ime.translate.TranslationSuppressionReason
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * Reads the current selection from `EditorInstance` and routes it through
     * [TranslationRouter], which owns addon consent, sensitive-field guards,
     * language-pack availability, sentence splitting, and cache lookup. When no
     * translator/language pack is ready, the action surfaces a Toast instead of
     * committing anything back to the editor.
     *
     * The target locale is read from the user's preferred target in
     * [TranslationLanguagePackManager], falling back to `"en"`.
     */
    @Serializable
    @SerialName("translate_selection")
    data object TranslateSelection : QuickAction() {
        override fun onPointerUp(context: Context) {
            val editorInstance by context.editorInstance()
            val raw = editorInstance.activeContent.selectedText
            if (raw.isBlank()) return
            val activeInfo = editorInstance.activeInfo
            val prefs by FlorisPreferenceStore
            val request = TranslationRouter.Request(
                sourceText = raw,
                targetLocale = TranslationLanguagePackManager.preferredTargetLocale() ?: "en",
                inputType = activeInfo.inputAttributes.raw,
                imeOptions = activeInfo.imeOptions.raw,
            )
            val router = TranslationRouter(
                translator = InlineTranslatorRegistry.active,
                packManager = TranslationRouter.PackManagerView.from(),
                isConsentGranted = {
                    prefs.privacy.translationConsent.get().allowsInvocation()
                },
            )
            val scope = context.quickActionCoroutineScope()
            if (scope == null) {
                Toast.makeText(
                    context,
                    R.string.quick_action__translation_unavailable_context,
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            scope.launch {
                val response = withContext(Dispatchers.IO) {
                    router.translate(request)
                }
                when (response) {
                    is TranslationRouter.Response.Translated -> {
                        if (editorInstance.activeContent.selectedText == raw) {
                            editorInstance.commitText(response.translatedText)
                        } else {
                            Toast.makeText(
                                context,
                                R.string.quick_action__translation_selection_changed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    is TranslationRouter.Response.Suppressed -> {
                        translateSelectionSuppressedMessageRes(response.reason)?.let { messageRes ->
                            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
                        }
                    }
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
            val selection = editorInstance.activeContent.selectedText
            val title = selection.ifBlank {
                editorInstance.activeContent.textBeforeSelection.trim().takeLast(140)
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

private fun Context.quickActionCoroutineScope(): CoroutineScope? {
    return (this as? LifecycleOwner)?.lifecycleScope
}

internal fun translateSelectionSuppressedMessageRes(reason: TranslationSuppressionReason): Int? {
    return when (reason) {
        TranslationSuppressionReason.BlankInput -> null
        TranslationSuppressionReason.ConsentRequired ->
            R.string.quick_action__translation_consent_required
        TranslationSuppressionReason.SensitiveField ->
            R.string.quick_action__translation_sensitive_field
        TranslationSuppressionReason.SourceEqualsTarget ->
            R.string.quick_action__translation_same_language
        TranslationSuppressionReason.SourceLocaleDetectionFailed ->
            R.string.quick_action__translation_source_detection_failed
        TranslationSuppressionReason.NoTargetLocaleResolved ->
            R.string.quick_action__translation_target_missing
        TranslationSuppressionReason.NoInstalledPair,
        TranslationSuppressionReason.TranslatorUnavailable,
        -> R.string.quick_action__translation_pair_unavailable
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
