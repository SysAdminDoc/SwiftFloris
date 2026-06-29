/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

/**
 * Enum class defining the display mode for the candidates.
 */
enum class CandidatesDisplayMode {
    CLASSIC,
    DYNAMIC,
    DYNAMIC_SCROLLABLE;
}

internal object CandidatesDisplayPolicy {
    private const val CLASSIC_MAX_VISIBLE_CANDIDATES = 3

    fun visibleCandidateCount(displayMode: CandidatesDisplayMode, candidateCount: Int): Int {
        val safeCandidateCount = candidateCount.coerceAtLeast(0)
        return when (displayMode) {
            CandidatesDisplayMode.CLASSIC -> {
                safeCandidateCount.coerceAtMost(CLASSIC_MAX_VISIBLE_CANDIDATES)
            }
            CandidatesDisplayMode.DYNAMIC,
            CandidatesDisplayMode.DYNAMIC_SCROLLABLE,
            -> safeCandidateCount
        }
    }

    fun <T> visibleCandidates(displayMode: CandidatesDisplayMode, candidates: List<T>): List<T> {
        return candidates.take(visibleCandidateCount(displayMode, candidates.size))
    }

    fun isHorizontallyScrollable(displayMode: CandidatesDisplayMode, candidateCount: Int): Boolean {
        return displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidateCount > 1
    }
}
