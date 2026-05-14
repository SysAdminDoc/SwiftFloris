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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val WcagAaTextContrast = 4.5

class ThemeContrastTest : FunSpec({
    test("Tokyo Night keyboard text tokens meet WCAG AA contrast") {
        val colors = loadColorResources("colors_theme_tokyo_night.xml")

        assertTextContrast(
            colors = colors,
            textTokens = listOf(
                "theme_tokyo_night_text_primary",
                "theme_tokyo_night_text_secondary",
                "theme_tokyo_night_text_tertiary",
            ),
            surfaceTokens = listOf(
                "theme_tokyo_night_surface",
                "theme_tokyo_night_background",
            ),
        )
    }

    test("Catppuccin Mocha keyboard text tokens meet WCAG AA contrast") {
        val colors = loadColorResources("colors_theme_catppuccin.xml")

        assertTextContrast(
            colors = colors,
            textTokens = listOf(
                "theme_catppuccin_text_primary",
                "theme_catppuccin_text_secondary",
                "theme_catppuccin_text_tertiary",
            ),
            surfaceTokens = listOf(
                "theme_catppuccin_surface",
                "theme_catppuccin_background",
            ),
        )
    }
})

private fun assertTextContrast(
    colors: Map<String, ColorRgb>,
    textTokens: List<String>,
    surfaceTokens: List<String>,
) {
    textTokens.forEach { textToken ->
        surfaceTokens.forEach { surfaceToken ->
            val contrast = contrastRatio(
                foreground = colors.getValue(textToken),
                background = colors.getValue(surfaceToken),
            )
            withClue("$textToken on $surfaceToken contrast ${"%.2f".format(contrast)}") {
                (contrast >= WcagAaTextContrast) shouldBe true
            }
        }
    }
}

private fun loadColorResources(fileName: String): Map<String, ColorRgb> {
    val file = locateColorResource(fileName)
    val rawValues = ColorResourceRegex.findAll(file.readText())
        .associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }

    fun resolve(name: String, seen: Set<String> = emptySet()): String {
        require(name !in seen) { "Circular @color reference while resolving $name in ${file.path}" }
        val value = rawValues[name] ?: error("Missing color resource $name in ${file.path}")
        return if (value.startsWith("@color/")) {
            resolve(value.removePrefix("@color/"), seen + name)
        } else {
            value
        }
    }

    return rawValues.keys.associateWith { name -> parseColor(resolve(name)) }
}

private fun locateColorResource(fileName: String): File {
    val candidates = listOf(
        File("app/src/main/res/values/$fileName"),
        File("src/main/res/values/$fileName"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("$fileName not reachable from working directory ${File(".").absolutePath}")
}

private val ColorResourceRegex = Regex("""<color\s+name="([^"]+)">\s*([^<]+)\s*</color>""")

private data class ColorRgb(val red: Int, val green: Int, val blue: Int)

private fun parseColor(value: String): ColorRgb {
    val hex = value.removePrefix("#")
    val rgb = when (hex.length) {
        6 -> hex
        8 -> hex.drop(2)
        else -> error("Unsupported color value $value")
    }
    return ColorRgb(
        red = rgb.substring(0, 2).toInt(16),
        green = rgb.substring(2, 4).toInt(16),
        blue = rgb.substring(4, 6).toInt(16),
    )
}

private fun contrastRatio(foreground: ColorRgb, background: ColorRgb): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun ColorRgb.relativeLuminance(): Double {
    return 0.2126 * red.linearized() +
        0.7152 * green.linearized() +
        0.0722 * blue.linearized()
}

private fun Int.linearized(): Double {
    val channel = this / 255.0
    return if (channel <= 0.03928) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}
