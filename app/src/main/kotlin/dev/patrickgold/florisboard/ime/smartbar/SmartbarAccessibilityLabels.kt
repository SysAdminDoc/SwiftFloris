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

import org.florisboard.lib.kotlin.curlyFormat

/**
 * Pure formatting helpers for smartbar TalkBack announcements. The actual
 * announcement templates are string resources (`a11y__candidate__*`,
 * `a11y__smartbar_action__fallback`) resolved at the composable call sites so
 * announcements localize with the rest of the app; this object only clamps
 * position values and fills the `{index}` / `{count}` / `{text}` placeholders.
 */
internal object SmartbarAccessibilityLabels {
    fun candidateLabel(
        template: String,
        text: String,
        index: Int,
        count: Int,
    ): String {
        val safeIndex = index.coerceAtLeast(0) + 1
        val safeCount = count.coerceAtLeast(safeIndex)
        return template.curlyFormat(
            "index" to safeIndex.toString(),
            "count" to safeCount.toString(),
            "text" to text,
        )
    }

    fun quickActionLabel(displayName: String, tooltip: String, fallback: String): String {
        return displayName.ifBlank {
            tooltip.ifBlank { fallback }
        }
    }
}
