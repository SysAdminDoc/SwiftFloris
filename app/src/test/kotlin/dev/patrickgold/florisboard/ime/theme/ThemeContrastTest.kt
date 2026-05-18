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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.color.neutralDynamicColorScheme
import org.florisboard.lib.compose.FlorisCardDefaults
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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

    test("bundled stylesheets keep keyboard, candidate row, and clipboard dialog text at WCAG AA contrast") {
        locateBundledStylesheetDirectory()
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val stylesheet = loadSnyggStylesheet(file)
                SnyggContrastCases.forEach { contrastCase ->
                    val foreground = stylesheet.colorFor(contrastCase.selector, "foreground")
                    val background = stylesheet.optionalColorFor(contrastCase.selector, "background")
                        ?: contrastCase.fallbackBackgroundSelector?.let { selector ->
                            stylesheet.colorFor(selector, "background")
                        }
                        ?: contrastCase.fallbackBackgroundToken?.let { token ->
                            stylesheet.resolveColor("var($token)")
                        }
                        ?: error("Missing background for ${contrastCase.selector} in ${file.path}")

                    assertContrast(
                        label = "${file.name} ${contrastCase.selector}",
                        foreground = foreground,
                        background = background,
                    )
                }
            }
    }

    test("settings warning, error, and dialog colors meet WCAG AA contrast") {
        ColorMappings.colors.forEach { accent ->
            val accentLabel = accent.toColorRgb().toHexLabel()
            listOf(
                "light" to neutralDynamicColorScheme(primary = accent, isDark = false),
                "dark" to neutralDynamicColorScheme(primary = accent, isDark = true),
                "amoled" to neutralDynamicColorScheme(primary = accent, isDark = true, isAmoled = true),
            ).forEach { (schemeName, scheme) ->
                val label = "$schemeName accent $accentLabel"

                assertContrast(
                    label = "$label warning card",
                    foreground = scheme.onTertiaryContainer.toColorRgb(),
                    background = scheme.tertiaryContainer.toColorRgb(),
                )
                assertContrast(
                    label = "$label warning card secondary",
                    foreground = scheme.onTertiaryContainer.toColorRgb().compositeOver(
                        background = scheme.tertiaryContainer.toColorRgb(),
                        alpha = FlorisCardDefaults.SecondaryContentAlpha,
                    ),
                    background = scheme.tertiaryContainer.toColorRgb(),
                )
                assertContrast(
                    label = "$label error card",
                    foreground = scheme.onErrorContainer.toColorRgb(),
                    background = scheme.errorContainer.toColorRgb(),
                )
                assertContrast(
                    label = "$label error card secondary",
                    foreground = scheme.onErrorContainer.toColorRgb().compositeOver(
                        background = scheme.errorContainer.toColorRgb(),
                        alpha = FlorisCardDefaults.SecondaryContentAlpha,
                    ),
                    background = scheme.errorContainer.toColorRgb(),
                )
                assertContrast(
                    label = "$label settings dialog text",
                    foreground = scheme.onSurface.toColorRgb(),
                    background = scheme.surfaceContainerHigh.toColorRgb(),
                )
                assertContrast(
                    label = "$label settings dialog secondary text",
                    foreground = scheme.onSurfaceVariant.toColorRgb(),
                    background = scheme.surfaceContainerHigh.toColorRgb(),
                )
            }
        }
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
        assertContrast(
            label = "$textToken on $surfaceToken",
            foreground = colors.getValue(textToken),
            background = colors.getValue(surfaceToken),
            minContrast = minContrast,
        )
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
    return File(locateBundledStylesheetDirectory(), fileName)
        .takeIf { it.exists() }
        ?: error("$fileName not reachable from working directory ${File(".").absolutePath}")
}

private fun locateBundledStylesheetDirectory(): File {
    val candidates = listOf(
        File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets"),
        File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("theme stylesheets not reachable from working directory ${File(".").absolutePath}")
}

private val SnyggContrastCases = listOf(
    SnyggContrastCase(selector = "key", fallbackBackgroundToken = "--background"),
    SnyggContrastCase(selector = "key:pressed"),
    SnyggContrastCase(selector = "key[code=10]"),
    SnyggContrastCase(selector = "key[code=10]:pressed"),
    SnyggContrastCase(selector = "smartbar-candidate-word", fallbackBackgroundToken = "--background"),
    SnyggContrastCase(selector = "smartbar-candidate-word:pressed"),
    SnyggContrastCase(selector = "smartbar-candidate-clip", fallbackBackgroundToken = "--background"),
    SnyggContrastCase(selector = "smartbar-candidate-clip:pressed"),
    SnyggContrastCase(selector = "clipboard-clear-all-dialog"),
    SnyggContrastCase(
        selector = "clipboard-clear-all-dialog-button",
        fallbackBackgroundSelector = "clipboard-clear-all-dialog",
    ),
)

private data class SnyggContrastCase(
    val selector: String,
    val fallbackBackgroundToken: String? = null,
    val fallbackBackgroundSelector: String? = null,
)

private fun loadSnyggStylesheet(file: File): SnyggStylesheet {
    val root = Json.parseToJsonElement(file.readText()).jsonObject
    val defines = root.getValue("@defines").jsonObject
        .mapValues { (_, value) -> value.jsonPrimitive.content }
    return SnyggStylesheet(file = file, defines = defines, root = root)
}

private data class SnyggStylesheet(
    val file: File,
    val defines: Map<String, String>,
    val root: kotlinx.serialization.json.JsonObject,
) {
    fun colorFor(selector: String, attr: String): ColorRgb {
        return optionalColorFor(selector = selector, attr = attr)
            ?: error("Transparent $attr for $selector in ${file.path} needs a fallback")
    }

    fun optionalColorFor(selector: String, attr: String): ColorRgb? {
        val rule = root[selector]?.jsonObject
            ?: error("Missing $selector in ${file.path}")
        val expression = rule[attr]?.jsonPrimitive?.content
            ?: error("Missing $attr for $selector in ${file.path}")
        return resolveColor(expression)
    }

    fun resolveColor(expression: String): ColorRgb? {
        val value = expression.trim()
        if (value == "transparent") return null
        VarColorRegex.matchEntire(value)?.let { match ->
            return resolveColor(defines.getValue(match.groupValues[1]))
        }
        return parseColor(value)
    }
}

private val VarColorRegex = Regex("""var\((--[^)]+)\)""")

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

private fun Color.toColorRgb(): ColorRgb {
    return ColorRgb(
        red = (red * 255).roundToInt(),
        green = (green * 255).roundToInt(),
        blue = (blue * 255).roundToInt(),
    )
}

private fun ColorRgb.toHexLabel(): String {
    return "#%02x%02x%02x".format(red, green, blue)
}

private fun ColorRgb.compositeOver(
    background: ColorRgb,
    alpha: Float,
): ColorRgb {
    return ColorRgb(
        red = compositeChannel(red, background.red, alpha),
        green = compositeChannel(green, background.green, alpha),
        blue = compositeChannel(blue, background.blue, alpha),
    )
}

private fun compositeChannel(foreground: Int, background: Int, alpha: Float): Int {
    return (foreground * alpha + background * (1f - alpha)).roundToInt()
}

private fun assertContrast(
    label: String,
    foreground: ColorRgb,
    background: ColorRgb,
    minContrast: Double = WcagAaTextContrast,
) {
    val contrast = contrastRatio(foreground = foreground, background = background)
    withClue("$label contrast ${"%.2f".format(contrast)}") {
        (contrast >= minContrast) shouldBe true
    }
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
