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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import java.util.Locale

object SmartbarActionProfiles {
    fun apply(
        base: QuickActionArrangement,
        editorInfo: FlorisEditorInfo,
        enabled: Boolean,
    ): QuickActionArrangement {
        if (!enabled) return base
        val profile = detect(editorInfo) ?: return base
        return base.prioritize(profile.priorityActions)
    }

    internal fun detect(editorInfo: FlorisEditorInfo): SmartbarActionProfile? {
        val variation = editorInfo.inputAttributes.variation
        val packageName = editorInfo.packageName.orEmpty().lowercase(Locale.ROOT)
        return when {
            variation.isPassword() -> SmartbarActionProfile.PASSWORD
            packageName.isChatApp() || variation == InputAttributes.Variation.SHORT_MESSAGE -> {
                SmartbarActionProfile.CHAT
            }
            packageName.isEmailApp() || variation.isEmail() -> SmartbarActionProfile.EMAIL
            else -> null
        }
    }

    private fun QuickActionArrangement.prioritize(priorityActions: List<QuickAction>): QuickActionArrangement {
        val sticky = stickyAction
        val priority = priorityActions
            .filterNot { it == sticky }
            .distinct()
        val existing = buildList {
            addAll(dynamicActions)
            addAll(hiddenActions)
        }.filterNot { it == sticky }
        val profiledDynamicActions = (priority + existing)
            .distinct()
        return copy(
            dynamicActions = profiledDynamicActions,
            hiddenActions = hiddenActions.filterNot { it in priority },
        ).distinct()
    }

    private fun InputAttributes.Variation.isPassword(): Boolean {
        return this == InputAttributes.Variation.PASSWORD ||
            this == InputAttributes.Variation.VISIBLE_PASSWORD ||
            this == InputAttributes.Variation.WEB_PASSWORD
    }

    private fun InputAttributes.Variation.isEmail(): Boolean {
        return this == InputAttributes.Variation.EMAIL_ADDRESS ||
            this == InputAttributes.Variation.EMAIL_SUBJECT ||
            this == InputAttributes.Variation.WEB_EMAIL_ADDRESS
    }

    private fun String.isChatApp(): Boolean {
        return contains("slack") ||
            contains("whatsapp") ||
            contains("telegram") ||
            contains("signal") ||
            contains("discord")
    }

    private fun String.isEmailApp(): Boolean {
        return contains("outlook") ||
            contains("gmail") ||
            contains("email")
    }
}

enum class SmartbarActionProfile(
    val priorityActions: List<QuickAction>,
) {
    PASSWORD(
        listOf(
            QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE),
            QuickAction.InsertKey(TextKeyData.TOGGLE_INCOGNITO_MODE),
            QuickAction.InsertKey(TextKeyData.IME_HIDE_UI),
            QuickAction.InsertKey(TextKeyData.SETTINGS),
        )
    ),
    CHAT(
        listOf(
            QuickAction.InsertKey(TextKeyData.IME_UI_MODE_MEDIA),
            QuickAction.InsertKey(TextKeyData.VOICE_INPUT),
            QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE),
            QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH),
            QuickAction.InsertKey(TextKeyData.TOGGLE_AUTOCORRECT),
        )
    ),
    EMAIL(
        listOf(
            QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE),
            QuickAction.InsertKey(TextKeyData.VOICE_INPUT),
            QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH),
            QuickAction.InsertKey(TextKeyData.ARROW_LEFT),
            QuickAction.InsertKey(TextKeyData.ARROW_RIGHT),
            QuickAction.InsertKey(TextKeyData.SETTINGS),
        )
    ),
}
