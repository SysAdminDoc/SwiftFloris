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
import dev.patrickgold.florisboard.ime.profile.PerAppGestureSet
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import java.util.Locale

object SmartbarActionProfiles {
    fun apply(
        base: QuickActionArrangement,
        editorInfo: FlorisEditorInfo,
        enabled: Boolean,
        forcedGestureSet: PerAppGestureSet = PerAppGestureSet.FOLLOW_GLOBAL,
    ): QuickActionArrangement {
        if (!enabled) return base
        val profile = forcedGestureSet.toSmartbarProfile() ?: detect(editorInfo) ?: return base
        return base.prioritize(profile.priorityActions)
    }

    private fun PerAppGestureSet.toSmartbarProfile(): SmartbarActionProfile? {
        return when (this) {
            PerAppGestureSet.CHAT -> SmartbarActionProfile.CHAT
            PerAppGestureSet.CODE -> SmartbarActionProfile.CODE
            else -> null
        }
    }

    internal fun detect(editorInfo: FlorisEditorInfo): SmartbarActionProfile? {
        val variation = editorInfo.inputAttributes.variation
        val packageName = editorInfo.packageName.orEmpty().lowercase(Locale.ROOT)
        return when {
            variation.isPassword() -> SmartbarActionProfile.PASSWORD
            // ROADMAP §7 Next-8.2 — per-app code-mode activation. The CODE
            // profile surfaces tab/esc/arrow keys so terminal + IDE users
            // don't have to switch to the symbol layer for the keys they
            // hit most. Check the package name match BEFORE the variation
            // gate below because IDEs often use SHORT_MESSAGE / generic
            // text variations and we don't want chat-mode to win.
            packageName.isCodeApp() -> SmartbarActionProfile.CODE
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

    /**
     * ROADMAP §7 Next-8.2 — packages that should auto-activate programmer
     * mode. Curated list of mainstream Android terminals, code editors,
     * and SSH clients. Substring match (lowercased) so the same matcher
     * works for the typical reverse-DNS shapes (`com.termux`,
     * `com.foxdebug.acode`, `com.sonelli.juicessh`, etc.).
     */
    private fun String.isCodeApp(): Boolean {
        return contains("termux") ||                      // Termux
            contains("juicessh") ||                       // JuiceSSH
            contains("foxdebug.acode") ||                 // Acode IDE
            contains("acode") ||                          // Acode/Pro
            contains("spck") ||                           // Spck Code Editor
            contains("quoda") ||                          // Quoda
            contains("github.android") ||                 // GitHub mobile
            contains("dev.editor") ||                     // Generic editor naming
            contains("git.client") ||                     // PocketGit / similar
            contains("jetbrains") ||                      // JetBrains family
            contains("codeeditor") ||
            contains("hackerterm") ||
            contains("connectbot") ||                     // ConnectBot
            contains("termius") ||                        // Termius
            contains("sshd") ||
            contains("vimtouch") ||                       // VimTouch
            contains("vim.android") ||
            contains("scummvm")  // not strictly code, but power-users want code keys here
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

    /**
     * ROADMAP §7 Next-8.1 + Next-8.2 — programmer / code-mode profile.
     * Surfaces Tab, Esc, arrow keys, and start/end-of-line jumps in the
     * smartbar so Termux, JuiceSSH, Acode, and similar users don't have to
     * dive into the symbol layer for every Tab/Esc keypress. The full
     * "Hacker's Keyboard" experience (modifier-key chords, swipe-to-symbol
     * on every key, regex snippets) belongs in a follow-up bottom-row
     * preset; this is the smartbar-surface MVP that ships now.
     */
    CODE(
        listOf(
            QuickAction.InsertKey(TextKeyData.TAB),
            QuickAction.InsertKey(TextKeyData.ESCAPE),
            QuickAction.InsertKey(TextKeyData.ARROW_LEFT),
            QuickAction.InsertKey(TextKeyData.ARROW_RIGHT),
            QuickAction.InsertKey(TextKeyData.ARROW_UP),
            QuickAction.InsertKey(TextKeyData.ARROW_DOWN),
            QuickAction.InsertKey(TextKeyData.MOVE_START_OF_LINE),
            QuickAction.InsertKey(TextKeyData.MOVE_END_OF_LINE),
            QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE),
            QuickAction.InsertKey(TextKeyData.SETTINGS),
        )
    ),
}
