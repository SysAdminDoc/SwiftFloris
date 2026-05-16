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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyVariation

/**
 * ROADMAP matrix #32 — quote / speech-mark auto-close.
 *
 * When the user types an opening quote-class character, decide whether the IME should additionally insert the
 * matching closing character and move the cursor between them. The HeliBoard request thread and r/SwiftKey complaint
 * threads both name this as table-stakes editor polish; SwiftKey, Gboard, and iOS all do this.
 *
 * The gate is intentionally pure (no `Context`, no `EditorInstance`) so the locale + apostrophe-context contract is
 * unit-tested without Robolectric. The wire-up in `KeyboardManager` is responsible for actually performing the
 * commit + cursor adjustment when this function returns non-null.
 *
 * ## Contract
 *
 * - Auto-close is suppressed for sensitive / non-prose contexts:
 *   - `KeyVariation.PASSWORD` (would corrupt the typed value)
 *   - `KeyVariation.URI` (URLs use single quotes structurally, e.g. JSON inside a URL fragment)
 *   - `KeyVariation.EMAIL_ADDRESS` (emails don't conventionally contain quotes)
 *
 * - Auto-close is suppressed when the cursor sits in the middle of a word (i.e. the next character is alphabetic).
 *   Auto-closing there would break the word: `hel"|lo` → `hel"|"lo` is jarring.
 *
 * - Auto-close is suppressed when the next character is exactly the closer we would insert. This avoids the most
 *   common double-up bug — the user typing the closing quote at the end of `"|"` quoted region should not result in
 *   `""|""`. A future slice may extend this into a full "overtype the closer instead of duplicating" behavior.
 *
 * - For the single quote `'`, auto-close is additionally suppressed when the previous character is a letter (the
 *   typed `'` is an apostrophe, e.g. `don't`, `I'm`) or a digit (foot/inch shorthand: `5'10"`).
 *
 * ## Locale handling
 *
 * The closer table is keyed by the literal opening character that was typed. Locale-specific opening characters
 * (German low-9 `„`, French guillemet `«`, CJK corner brackets `「` / `『`, curly quotes `“` `‘`) are looked up
 * directly. The IME does **not** silently substitute one opening character for another based on locale here — that
 * is a separate concern handled by the smart-quote substitution path (not in scope for this slice). This gate only
 * answers: "given that the user just typed X, what is the matching closer if we should auto-close?"
 */
object QuoteAutoCloseGate {

    /**
     * Decide whether to auto-close after [typedChar] was committed.
     *
     * @param typedChar the character that was just committed via `editorInstance.commitChar`.
     * @param precedingText the text immediately before the cursor (the just-typed [typedChar] is included).
     * @param followingText the text immediately after the cursor.
     * @param variation the active [KeyVariation] for the editor.
     * @param autoCloseEnabled the user-controlled pref toggle.
     *
     * @return the closer string to insert + back-up over, or `null` if no auto-close should happen.
     */
    fun closerFor(
        typedChar: String,
        precedingText: String,
        followingText: String,
        variation: KeyVariation,
        autoCloseEnabled: Boolean,
    ): String? {
        if (!autoCloseEnabled) return null
        if (!isSafeVariation(variation)) return null

        val candidateCloser = matchCloser(typedChar) ?: return null

        if (followingText.startsWith(candidateCloser)) return null

        if (followingText.isNotEmpty() && followingText[0].isLetterOrDigit()) {
            return null
        }

        if (typedChar == "'" || typedChar == "’") {
            val precedingWithoutTyped = precedingText.dropLast(typedChar.length)
            val prev = precedingWithoutTyped.lastOrNull()
            if (prev != null && (prev.isLetter() || prev.isDigit())) return null
        }

        return candidateCloser
    }

    private fun matchCloser(typedChar: String): String? = when (typedChar) {
        "\"" -> "\""
        "'" -> "'"
        "«" -> "»"      // «  »
        "„" -> "“"      // „  “ (German low-9 → high-66)
        "‚" -> "‘"      // ‚  ‘ (German low-9 single → high-66 single)
        "“" -> "”"      // “  ” (curly double left → right)
        "‘" -> "’"      // ‘  ’ (curly single left → right)
        "「" -> "」"      // 「 」 CJK corner
        "『" -> "』"      // 『 』 CJK white corner
        "〈" -> "〉"      // 〈 〉 CJK angle
        "《" -> "》"      // 《 》 CJK double angle
        else -> null
    }

    private fun isSafeVariation(variation: KeyVariation): Boolean = when (variation) {
        KeyVariation.PASSWORD,
        KeyVariation.URI,
        KeyVariation.EMAIL_ADDRESS,
        -> false
        KeyVariation.NORMAL,
        KeyVariation.ALL,
        -> true
    }
}
