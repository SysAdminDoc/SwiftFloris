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

package dev.patrickgold.florisboard.ime.nlp

internal object AutoCorrectConfidencePolicy {
    const val MIN_PERCENT = 50
    const val MAX_PERCENT = 100
    const val DEFAULT_PERCENT = 50
    const val DEFAULT_THRESHOLD = DEFAULT_PERCENT / 100.0
    const val LEGACY_THRESHOLD = 0.0

    fun thresholdFor(percent: Int): Double {
        return percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100.0
    }

    fun allows(candidateConfidence: Double, threshold: Double): Boolean {
        return candidateConfidence.coerceIn(0.0, 1.0) >= threshold.coerceIn(0.0, 1.0)
    }

    fun allows(candidateConfidence: Double, thresholdPercent: Int): Boolean {
        return allows(candidateConfidence, thresholdFor(thresholdPercent))
    }
}
