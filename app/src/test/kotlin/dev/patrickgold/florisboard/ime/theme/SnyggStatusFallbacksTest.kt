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
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldNotBe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The emoji tag and pin sheets render validation errors with whatever the
 * `media-emoji-pin-sheet-error` element resolves to, and no bundled stylesheet
 * defines that element. The fallback is therefore the real colour on every
 * shipped theme, so it has to stay readable on both kinds of surface.
 */
class SnyggStatusFallbacksTest : FunSpec({

    // Panel backgrounds taken from the bundled stylesheets that the emoji
    // sheets actually draw on top of.
    val lightSurfaces = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF2F2F7),
        Color(0xFFE8EAF0),
    )
    val darkSurfaces = listOf(
        Color(0xFF000000),
        Color(0xFF171923),
        Color(0xFF1E1E2E),
    )

    test("every bundled surface gets an error tone that clears WCAG AA") {
        for (surface in lightSurfaces + darkSurfaces) {
            val contrast = contrastRatio(snyggErrorForegroundFor(surface), surface)
            withClue(surface) {
                contrast shouldBeGreaterThanOrEqual 4.5
            }
        }
    }

    test("light and dark surfaces do not receive the same error tone") {
        snyggErrorForegroundFor(lightSurfaces.first()) shouldNotBe
            snyggErrorForegroundFor(darkSurfaces.first())
    }

    test("a single hardcoded tone could not have satisfied both") {
        // Guards the shape of the fix: if someone collapses this back to one
        // literal, one side of the split necessarily fails the assertion above.
        val darkTone = snyggErrorForegroundFor(darkSurfaces.first())
        contrastRatio(darkTone, lightSurfaces.first()) shouldBeLessThan 4.5
    }
})

private infix fun Double.shouldBeLessThan(other: Double) {
    check(this < other) { "expected $this < $other" }
}

private fun <T> withClue(clue: Any?, block: () -> T): T {
    return try {
        block()
    } catch (error: AssertionError) {
        throw AssertionError("${error.message} (surface=$clue)", error)
    }
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = max(foreground.relativeLuminance(), background.relativeLuminance())
    val darker = min(foreground.relativeLuminance(), background.relativeLuminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    fun linearize(channel: Float): Double {
        val normalized = channel.toDouble()
        return if (normalized <= 0.03928) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
}
