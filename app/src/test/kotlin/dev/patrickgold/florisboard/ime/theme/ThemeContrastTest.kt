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
private const val WcagAaaTextContrast = 7.0

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

    test("SwiftKey High Contrast stylesheet text tokens meet WCAG AAA contrast") {
        val colors = loadSnyggDefines("swiftkey_high_contrast.json")

        assertTextContrast(
            colors = colors,
            minContrast = WcagAaaTextContrast,
            textSurfacePairs = listOf(
                "--on-background" to "--background",
                "--on-background" to "--background-variant",
                "--on-surface" to "--surface",
                "--on-surface" to "--surface-variant",
                "--on-surface" to "--popup-surface",
                "--on-surface" to "--focused-popup-surface",
                "--on-surface-variant" to "--surface",
                "--on-primary" to "--primary",
                "--on-primary" to "--primary-variant",
            ),
        )
    }

    test("SwiftKey High Contrast and Aurora Animated are registered bundled stylesheets") {
        val manifest = locateThemeExtensionManifest().readText()
        manifest.contains("\"version\": \"0.4.0\"") shouldBe true
        manifest.contains("\"id\": \"swiftkey_high_contrast\"") shouldBe true
        manifest.contains("\"label\": \"SwiftKey High Contrast (AAA)\"") shouldBe true
        manifest.contains("\"id\": \"aurora_animated\"") shouldBe true
        manifest.contains("\"label\": \"Aurora Animated\"") shouldBe true
        locateBundledStylesheet("swiftkey_high_contrast.json").exists() shouldBe true
        locateBundledStylesheet("aurora_animated.json").exists() shouldBe true
    }
})

private fun assertTextContrast(
    colors: Map<String, ColorRgb>,
    textTokens: List<String>,
    surfaceTokens: List<String>,
    minContrast: Double = WcagAaTextContrast,
) {
    assertTextContrast(
        colors = colors,
        minContrast = minContrast,
        textSurfacePairs = textTokens.flatMap { textToken ->
            surfaceTokens.map { surfaceToken -> textToken to surfaceToken }
        },
    )
}

private fun assertTextContrast(
    colors: Map<String, ColorRgb>,
    minContrast: Double,
    textSurfacePairs: List<Pair<String, String>>,
) {
    textSurfacePairs.forEach { (textToken, surfaceToken) ->
        val contrast = contrastRatio(
            foreground = colors.getValue(textToken),
            background = colors.getValue(surfaceToken),
        )
        withClue("$textToken on $surfaceToken contrast ${"%.2f".format(contrast)}") {
            (contrast >= minContrast) shouldBe true
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

private fun loadSnyggDefines(fileName: String): Map<String, ColorRgb> {
    val file = locateBundledStylesheet(fileName)
    val body = StylesheetDefinesRegex.find(file.readText())?.groupValues?.get(1)
        ?: error("Missing @defines block in ${file.path}")
    return SnyggDefineColorRegex.findAll(body)
        .associate { match ->
            match.groupValues[1] to parseColor(match.groupValues[2])
        }
}

private fun locateThemeExtensionManifest(): File {
    val candidates = listOf(
        File("app/src/main/assets/ime/theme/org.florisboard.themes/extension.json"),
        File("src/main/assets/ime/theme/org.florisboard.themes/extension.json"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("theme extension manifest not reachable from working directory ${File(".").absolutePath}")
}

private fun locateBundledStylesheet(fileName: String): File {
    val candidates = listOf(
        File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
        File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("$fileName not reachable from working directory ${File(".").absolutePath}")
}

private val StylesheetDefinesRegex = Regex(""""@defines"\s*:\s*\{([\s\S]*?)\n\s*\}""")
private val SnyggDefineColorRegex = Regex(""""(--[^"]+)"\s*:\s*"(#[0-9a-fA-F]{6,8})"""")

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
