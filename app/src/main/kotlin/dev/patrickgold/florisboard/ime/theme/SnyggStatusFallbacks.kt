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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.apptheme.errorDark
import dev.patrickgold.florisboard.app.apptheme.errorLight
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Last-resort foreground for validation errors drawn on an IME surface whose
 * Snygg element declares no `foreground` of its own.
 *
 * A fixed literal cannot work here. None of the bundled stylesheets define
 * `media-emoji-pin-sheet-error`, and a user theme is free to skip it too, so
 * whatever sits in the `default` argument is what people actually read. A
 * dark-scheme tone hardcoded there is invisible on a light keyboard, which is
 * exactly the state the string is trying to escape.
 *
 * The tone is picked by measuring both candidates against [background] and
 * keeping whichever contrasts more. Measuring rather than thresholding matters:
 * a luminance cutoff has to guess where the two curves cross, and relative
 * luminance is not perceptual, so a mid-grey surface sits on the wrong side of
 * any round number you choose. Against `#B0B0B0` the light tone reaches 2.98:1
 * while the dark one manages 1.28:1, and a 0.5 cutoff picks the dark one.
 *
 * [behind] is what shows through when [background] is translucent, which Snygg
 * permits: `transparent` parses to a real colour value, so an element can
 * legitimately resolve to one. The two are composited before measuring so the
 * decision is made against what the eye actually sees.
 *
 * The candidates are the Material 3 error roles the Settings palette already
 * uses, so an error looks the same wherever it appears.
 */
fun snyggErrorForegroundFor(background: Color, behind: Color = Color.Black): Color {
    val surface = background.flattenOver(behind)
    return if (contrastRatio(errorLight, surface) >= contrastRatio(errorDark, surface)) {
        errorLight
    } else {
        errorDark
    }
}

private fun Color.flattenOver(behind: Color): Color {
    if (alpha >= 1f) return this
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = red * a + behind.red * (1f - a),
        green = green * a + behind.green * (1f - a),
        blue = blue * a + behind.blue * (1f - a),
        alpha = 1f,
    )
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = max(foreground.wcagLuminance(), background.wcagLuminance())
    val darker = min(foreground.wcagLuminance(), background.wcagLuminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.wcagLuminance(): Double {
    fun linearize(channel: Float): Double {
        val normalized = channel.toDouble()
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
}
