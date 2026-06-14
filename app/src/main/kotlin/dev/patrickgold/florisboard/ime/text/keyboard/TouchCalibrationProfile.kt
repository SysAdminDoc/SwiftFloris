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

package dev.patrickgold.florisboard.ime.text.keyboard

/**
 * User-selectable touch-resolution calibration profile.
 *
 * Adjacent-key false hits are a recurring FOSS-keyboard complaint (HeliBoard
 * #2549). SwiftFloris resolves taps through two cooperating mechanisms:
 *
 *  - **Gap rescue** ([TextKeyboard.getNearestKeyForPos]) snaps a tap that
 *    lands in the dead zone *between* keys onto the closest key within
 *    [gapRescueDistanceFactor] × min(key width, key height).
 *  - **Spatial model** ([AdaptiveTouchModel.refine]) re-attributes a tap to a
 *    learned neighbour when the user's accumulated offset distribution makes
 *    the neighbour the better fit, bounded by the neighbour tolerances and a
 *    minimum learned-sample count.
 *
 * A calibration profile exposes those previously-hardcoded knobs so users can
 * trade off between "never steal my tap" (conservative — narrow dead zones,
 * reluctant neighbour correction, lots of evidence required) and "fix my fat
 * fingers" (rescue-heavy — wide dead zones, eager correction, low evidence
 * threshold). [NORMAL] reproduces the historically-shipped constants exactly,
 * so the default behaviour is unchanged.
 *
 * The profile is consulted per touch-down; values are plain immutable data so
 * the policy is trivially unit-testable.
 */
enum class TouchCalibrationProfile(
    /** Multiplied by min(key width, key height) to bound gap-rescue distance. */
    val gapRescueDistanceFactor: Float,
    /** Horizontal neighbour-candidate window, in primary-key half-widths. */
    val neighbourHorizontalTolerance: Float,
    /** Vertical neighbour-candidate window, in primary-key half-heights. */
    val neighbourVerticalTolerance: Float,
    /** Minimum learned taps on a key before the spatial model may act on it. */
    val minSamplesPerKey: Int,
) {
    /** Tight: prefers the geometric hit, rarely rescues or re-attributes. */
    CONSERVATIVE(
        gapRescueDistanceFactor = 0.18f,
        neighbourHorizontalTolerance = 0.28f,
        neighbourVerticalTolerance = 0.45f,
        minSamplesPerKey = 45,
    ),

    /** Shipped default — identical to the pre-calibration hardcoded values. */
    NORMAL(
        gapRescueDistanceFactor = 0.32f,
        neighbourHorizontalTolerance = 0.40f,
        neighbourVerticalTolerance = 0.60f,
        minSamplesPerKey = 30,
    ),

    /** Loose: wide dead zones and eager neighbour correction for heavy thumbs. */
    RESCUE_HEAVY(
        gapRescueDistanceFactor = 0.48f,
        neighbourHorizontalTolerance = 0.55f,
        neighbourVerticalTolerance = 0.75f,
        minSamplesPerKey = 20,
    );

    companion object {
        val Default = NORMAL
    }
}
