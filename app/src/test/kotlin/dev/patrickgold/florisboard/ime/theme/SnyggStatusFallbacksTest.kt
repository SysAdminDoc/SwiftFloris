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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The emoji tag and pin sheets render validation errors with whatever the
 * `media-emoji-pin-sheet-error` element resolves to, and no bundled stylesheet
 * defines that element. The fallback is therefore the real colour on every
 * shipped theme, and on any user theme that also omits the rule, so it has to
 * stay readable on surfaces nobody has picked yet.
 */
class SnyggStatusFallbacksTest : FunSpec({

    val bundledLightSurfaces = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF2F2F7),
        Color(0xFFE8EAF0),
    )
    val bundledDarkSurfaces = listOf(
        Color(0xFF000000),
        Color(0xFF171923),
        Color(0xFF1E1E2E),
    )

    test("every bundled surface gets an error tone that clears WCAG AA") {
        for (surface in bundledLightSurfaces + bundledDarkSurfaces) {
            val contrast = contrastRatio(snyggErrorForegroundFor(surface), surface)
            check(contrast >= 4.5) { "surface $surface only reached $contrast:1" }
        }
    }

    test("light and dark surfaces do not receive the same error tone") {
        snyggErrorForegroundFor(bundledLightSurfaces.first()) shouldNotBe
            snyggErrorForegroundFor(bundledDarkSurfaces.first())
    }

    test("a mid-tone surface gets the better of the two tones, not the darker half") {
        // Relative luminance is not perceptual, so a luminance cutoff puts these
        // on the wrong side: #B0B0B0 sits at 0.434, below a 0.5 pivot, yet the
        // light tone reaches 2.98:1 there against the dark tone's 1.28:1.
        for (grey in listOf(Color(0xFFB0B0B0), Color(0xFF909090), Color(0xFFA0A0A0))) {
            val chosen = snyggErrorForegroundFor(grey)
            val rejected = if (chosen == Color(0xFFBA1A1A)) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
            check(contrastRatio(chosen, grey) >= contrastRatio(rejected, grey)) {
                "surface $grey got the worse tone"
            }
        }
    }

    test("no surface anywhere on the grey ramp gets the worse of the two tones") {
        for (step in 0..255) {
            val surface = Color(red = step / 255f, green = step / 255f, blue = step / 255f)
            val light = contrastRatio(Color(0xFFBA1A1A), surface)
            val dark = contrastRatio(Color(0xFFFFB4AB), surface)
            val chosen = snyggErrorForegroundFor(surface)
            val chosenContrast = if (chosen == Color(0xFFBA1A1A)) light else dark
            check(chosenContrast >= max(light, dark) - 1e-9) {
                "grey level $step got $chosen at $chosenContrast:1 when ${max(light, dark)}:1 was available"
            }
        }
    }

    test("a translucent surface is judged against what shows through it") {
        // Snygg parses `transparent` into a real colour value, so an element can
        // legitimately resolve to one. Reading its RGB alone would call a fully
        // transparent panel black and pick the tone for a dark surface, which is
        // then painted onto whatever light window is actually behind it.
        val clear = Color(0x00000000)
        snyggErrorForegroundFor(clear, behind = Color(0xFFFFFFFF)) shouldBe
            snyggErrorForegroundFor(Color(0xFFFFFFFF))
        snyggErrorForegroundFor(clear, behind = Color(0xFF171923)) shouldBe
            snyggErrorForegroundFor(Color(0xFF171923))
    }

    test("a half-transparent panel blends toward the window before the decision") {
        val halfDarkOnWhite = Color(0x80000000)
        val blended = Color(0xFF7F7F7F)
        snyggErrorForegroundFor(halfDarkOnWhite, behind = Color(0xFFFFFFFF)) shouldBe
            snyggErrorForegroundFor(blended)
    }
})

private fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = max(foreground.wcagLuminance(), background.wcagLuminance())
    val darker = min(foreground.wcagLuminance(), background.wcagLuminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.wcagLuminance(): Double {
    fun linearize(channel: Float): Double {
        val normalized = channel.toDouble()
        return if (normalized <= 0.03928) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
}
