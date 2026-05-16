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

package dev.patrickgold.florisboard.ime.clipboard

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType

/**
 * ROADMAP matrix #33 — clipboard search filter.
 *
 * Pure helper that filters a [ClipboardItem] list by a free-text query. Kept pure (no Android types, no
 * Compose) so the search contract is unit-testable without spinning up the IME / Robolectric stack.
 *
 * The companion retention policy already lives in `prefs.clipboard` (size limit, auto-clean old, auto-clean
 * sensitive); the search wire-up is the load-bearing missing piece.
 *
 * ## Filtering contract
 *
 * - Blank query (empty, whitespace-only) returns the input list unchanged. The user has not asked to filter.
 * - Non-blank query keeps every [ClipboardItem] whose [matches] predicate returns true. The predicate is:
 *   - For [ItemType.TEXT]: case-insensitive substring match on the item's text field.
 *   - For [ItemType.IMAGE] / [ItemType.VIDEO]: never matches a non-blank query. Media items don't carry
 *     searchable text and falling through to them would surprise the user (they would see media tiles in a
 *     "search" pane that don't appear to match anything).
 * - Sensitive items remain visible if their text matches — search does NOT auto-filter sensitive items.
 *   The user has explicitly searched; the lock-screen / sensitive-redaction policies are enforced upstream
 *   by the [ClipboardSuggestionLockGate] (matrix #34) and by the
 *   `prefs.clipboard.historyAutoCleanSensitive*` auto-clean policy.
 */
object ClipboardHistoryFilter {

    /**
     * Return the subset of [items] that match [query] under the contract above.
     *
     * Stable: preserves the input order and never reorders or de-duplicates.
     */
    fun filterByQuery(items: List<ClipboardItem>, query: String): List<ClipboardItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items
        val lowerQuery = trimmed.lowercase()
        return items.filter { matches(it, lowerQuery) }
    }

    /**
     * @param item the candidate.
     * @param lowerQuery the search query, already trimmed and lowercased.
     */
    fun matches(item: ClipboardItem, lowerQuery: String): Boolean {
        if (lowerQuery.isEmpty()) return true
        if (item.type != ItemType.TEXT) return false
        val text = item.text ?: return false
        return text.lowercase().contains(lowerQuery)
    }
}
