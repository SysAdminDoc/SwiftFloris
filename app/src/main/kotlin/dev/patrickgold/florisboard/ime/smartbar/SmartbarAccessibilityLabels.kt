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

package dev.patrickgold.florisboard.ime.smartbar

internal object SmartbarAccessibilityLabels {
    const val RemoveCandidateAction = "Remove from predictions"

    fun candidateLabel(
        text: String,
        index: Int,
        count: Int,
        isClipboard: Boolean,
        isAutoCommit: Boolean,
    ): String {
        val safeIndex = index.coerceAtLeast(0) + 1
        val safeCount = count.coerceAtLeast(safeIndex)
        val prefix = when {
            isClipboard -> "Clipboard suggestion"
            isAutoCommit -> "Autocorrect suggestion"
            else -> "Suggestion"
        }
        return "$prefix $safeIndex of $safeCount: $text"
    }

    fun quickActionLabel(displayName: String, tooltip: String): String {
        return displayName.ifBlank {
            tooltip.ifBlank { "Smartbar action" }
        }
    }
}
