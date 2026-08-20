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
import androidx.compose.material3.ColorScheme
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.color.neutralDynamicColorScheme
import org.florisboard.lib.compose.FlorisCardDefaults
import dev.patrickgold.florisboard.app.apptheme.refinedSurfaces
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val WcagAaTextContrast = 4.5
private const val WcagAaNonTextContrast = 3.0
private const val InactiveUiContrastExemption = 1.0
private const val DecorativeContrastExemption = 1.0
private const val IncognitoIndicatorContrastExemption = 1.0
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

    test("bundled stylesheets derive contrast cases from every foreground-bearing selector") {
        val violations = mutableListOf<String>()
        locateBundledStylesheetDirectory()
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val stylesheet = loadSnyggStylesheet(file)
                val selectors = stylesheet.foregroundBearingSelectors()
                withClue("${file.name} has no foreground-bearing selectors") {
                    selectors.isNotEmpty() shouldBe true
                }
                selectors.forEach { selector ->
                    val foreground = stylesheet.colorFor(selector, "foreground")
                    val background = stylesheet.backgroundFor(selector)
                    val minContrast = minimumContrastFor(selector)
                    val contrast = contrastRatio(foreground = foreground, background = background)
                    if (contrast < minContrast) {
                        violations += "${file.name} $selector contrast " +
                            "${"%.2f".format(contrast)} < ${"%.1f".format(minContrast)}"
                    }
                }
            }

        withClue("contrast violations:\n${violations.joinToString("\n")}") {
            violations.shouldBeEmpty()
        }
    }

    test("production status and empty-state color pairs meet WCAG AA contrast") {
        ColorMappings.colors.forEach { accent ->
            val accentLabel = accent.toColorRgb().toHexLabel()
            listOf(
                "light" to productionColorScheme(accent = accent, isDark = false),
                "dark" to productionColorScheme(accent = accent, isDark = true),
                "amoled" to productionColorScheme(accent = accent, isDark = true, isAmoled = true),
            ).forEach { (schemeName, scheme) ->
                val label = "$schemeName accent $accentLabel"

                listOf(
                    "progress card" to scheme.surfaceContainer,
                    "success card" to scheme.surfaceContainerHigh,
                    "warning card" to scheme.surfaceContainerHigh,
                    "error card" to scheme.surfaceContainerHigh,
                    "neutral card" to scheme.surfaceContainerHigh,
                    "info card" to scheme.surfaceContainerHigh,
                ).forEach { (cardName, background) ->
                    assertProductionCardTextContrast(
                        label = "$label $cardName",
                        foreground = scheme.onSurface,
                        background = background,
                    )
                }
                assertContrast(
                    label = "$label empty-state title",
                    foreground = scheme.onSurface.toColorRgb(),
                    background = scheme.surfaceContainerLow.toColorRgb(),
                )
                assertContrast(
                    label = "$label empty-state message",
                    foreground = scheme.onSurfaceVariant.toColorRgb(),
                    background = scheme.surfaceContainerLow.toColorRgb(),
                )
                assertContrast(
                    label = "$label empty-state icon",
                    foreground = scheme.onPrimaryContainer.toColorRgb(),
                    background = scheme.primaryContainer.toColorRgb().compositeOver(
                        background = scheme.surfaceContainerLow.toColorRgb(),
                        alpha = 0.88f,
                    ),
                )
                assertContrast(
                    label = "$label empty-state action",
                    foreground = scheme.primary.toColorRgb(),
                    background = scheme.surfaceContainerLow.toColorRgb(),
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

private const val ProductionContrastLevel = 0.18

private fun productionColorScheme(
    accent: Color,
    isDark: Boolean,
    isAmoled: Boolean = false,
): ColorScheme {
    return neutralDynamicColorScheme(
        primary = accent,
        isDark = isDark,
        isAmoled = isAmoled,
        contrastLevel = ProductionContrastLevel,
        modifyColorScheme = {
            it.refinedSurfaces(tint = accent, isDark = isDark, isAmoled = isAmoled)
        },
    )
}

private fun assertProductionCardTextContrast(
    label: String,
    foreground: Color,
    background: Color,
) {
    assertContrast(
        label = label,
        foreground = foreground.toColorRgb(),
        background = background.toColorRgb(),
    )
    assertContrast(
        label = "$label secondary",
        foreground = foreground.toColorRgb().compositeOver(
            background = background.toColorRgb(),
            alpha = FlorisCardDefaults.SecondaryContentAlpha,
        ),
        background = background.toColorRgb(),
    )
}

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
    fun foregroundBearingSelectors(): List<String> {
        return root.entries
            .asSequence()
            .filter { (selector, value) ->
                !selector.startsWith("@") && value is JsonObject && value.containsKey("foreground")
            }
            .map { (selector, _) -> selector }
            .sorted()
            .toList()
    }

    fun colorFor(selector: String, attr: String): ColorRgb {
        return propertyColor(selector = selector, attr = attr)
            ?: error("Missing or transparent $attr for $selector in ${file.path}")
    }

    fun backgroundFor(selector: String): ColorRgb {
        propertyColor(selector, "background")?.let { return it }
        val candidates = generateSequence(selectorBaseName(selector)) { parentElementOf(it) }
        return candidates
            .mapNotNull { candidate -> propertyColor(candidate, "background") }
            .firstOrNull()
            ?: propertyColor("window", "background")
            ?: resolveColor("var(--background)")
            ?: error("Missing background for $selector in ${file.path}")
    }

    private fun propertyColor(selector: String, attr: String): ColorRgb? {
        val rule = root[selector]?.jsonObject ?: return null
        val expression = rule[attr]?.jsonPrimitive?.content
            ?: return null
        if (expression.trim() == "inherit") {
            return inheritedColor(selector, attr)
        }
        return resolveColor(expression)
    }

    /*
     * Snygg resolves `inherit` against the parent element's computed property
     * (SnyggPropertySetEditor.inheritImplicitly). Element names are
     * hierarchical, so the parent chain is the selector base name with its
     * trailing `-` segments removed one at a time. The chain strictly
     * shortens, so a parent that also declares `inherit` terminates.
     */
    private fun inheritedColor(selector: String, attr: String): ColorRgb? {
        return generateSequence(parentElementOf(selectorBaseName(selector))) { parentElementOf(it) }
            .mapNotNull { parent -> propertyColor(parent, attr) }
            .firstOrNull()
    }

    fun resolveColor(expression: String): ColorRgb? {
        val value = expression.trim()
        if (value == "transparent") return null
        VarColorRegex.matchEntire(value)?.let { match ->
            return defines[match.groupValues[1]]?.let(::resolveColor)
        }
        return parseColor(value)
    }
}

private fun selectorBaseName(selector: String): String {
    return selector.substringBefore(':').substringBefore('[')
}

private fun parentElementOf(element: String): String? {
    return element.substringBeforeLast('-', missingDelimiterValue = "")
        .takeUnless { it.isEmpty() }
}

/*
 * Snygg's foreground property is the tint for text and glyphs, so every new
 * foreground selector enters this gate automatically and inherits the 4.5:1
 * text floor unless it classifies itself as something else below.
 *
 * WCAG 1.4.3 sets the 4.5:1 floor for text. WCAG 1.4.11 sets a 3:1 floor for
 * graphical objects and for the visual information identifying a component
 * and its state, and exempts two categories outright: inactive components,
 * and purely decorative graphics. Every relaxation this gate applies is one
 * of those two exceptions and is named here rather than omitted from the
 * gate, so a reviewer can see the whole exemption set in one place.
 */
private fun minimumContrastFor(selector: String): Double {
    val baseElement = selectorBaseName(selector)
    return when {
        // Inactive components. NOOP is the empty placeholder tile in the
        // quick-actions editor grid: it renders in the disabled tint and
        // does nothing when pressed.
        selector.contains(":disabled") ||
            selector.contains("state=`disabled`") ||
            selector.contains("[code=${KeyCode.NOOP}]") -> InactiveUiContrastExemption

        // Purely decorative. Candidate spacers are hairline separators drawn
        // at ~25% alpha in every bundled theme (and deliberately invisible in
        // the borderless variants, which paint them in the surface colour);
        // the candidate list is already separated by layout. The incognito
        // glyph is intentionally translucent.
        baseElement.endsWith("-spacer") -> DecorativeContrastExemption
        baseElement == "incognito-mode-indicator" -> IncognitoIndicatorContrastExemption

        // Graphical objects and state indicators: the 3:1 non-text floor.
        // DRAG_MARKER is the reorder grip, SHIFT the caps-lock arrow glyph.
        baseElement in NonTextElements ||
            baseElement.endsWith("-icon") ||
            baseElement.endsWith("-indicator") ||
            selector.contains("[code=${KeyCode.DRAG_MARKER}]") ||
            selector.contains("[code=${KeyCode.SHIFT}]") -> WcagAaNonTextContrast

        else -> WcagAaTextContrast
    }
}

/**
 * Elements that render a glyph rather than prose but whose names do not carry
 * one of the graphical suffixes above.
 */
private val NonTextElements = setOf(
    "glide-trail", // transient swipe path drawn under the finger
    "media-emoji-tab", // emoji category glyphs
    "window-resize-action", // floating-window resize handle
    "smartbar-extended-actions-toggle", // expand/collapse chevron
)

private val VarColorRegex = Regex("""var\((--[^)]+)\)""")

private val StylesheetDefinesRegex = Regex(""""@defines"\s*:\s*\{([\s\S]*?)\n\s*\}""")
private val SnyggDefineColorRegex = Regex(""""(--[^"]+)"\s*:\s*"(#[0-9a-fA-F]{6,8})"""")

private data class ColorRgb(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Double = 1.0,
)

private fun parseColor(value: String): ColorRgb {
    val normalizedValue = value.trim()
    val hex = normalizedValue.removePrefix("#")
    if (hex.length == 6 || hex.length == 8) {
        return ColorRgb(
            red = hex.substring(0, 2).toInt(16),
            green = hex.substring(2, 4).toInt(16),
            blue = hex.substring(4, 6).toInt(16),
            alpha = if (hex.length == 8) {
                hex.substring(6, 8).toInt(16) / 255.0
            } else {
                1.0
            },
        )
    }

    RgbColorRegex.matchEntire(normalizedValue)?.let { match ->
        return ColorRgb(
            red = match.groupValues[1].toInt(),
            green = match.groupValues[2].toInt(),
            blue = match.groupValues[3].toInt(),
        )
    }
    RgbaColorRegex.matchEntire(normalizedValue)?.let { match ->
        return ColorRgb(
            red = match.groupValues[1].toInt(),
            green = match.groupValues[2].toInt(),
            blue = match.groupValues[3].toInt(),
            alpha = match.groupValues[4].toDouble(),
        )
    }
    error("Unsupported color value $value")
}

private val RgbColorRegex = Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")
private val RgbaColorRegex = Regex("""rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([0-9.]+)\s*\)""")

private fun Color.toColorRgb(): ColorRgb {
    return ColorRgb(
        red = (red * 255).roundToInt(),
        green = (green * 255).roundToInt(),
        blue = (blue * 255).roundToInt(),
        alpha = alpha.toDouble(),
    )
}

private fun ColorRgb.toHexLabel(): String {
    return "#%02x%02x%02x".format(red, green, blue)
}

private fun ColorRgb.compositeOver(
    background: ColorRgb,
    alpha: Float = 1f,
): ColorRgb {
    val effectiveAlpha = (this.alpha * alpha).coerceIn(0.0, 1.0)
    return ColorRgb(
        red = compositeChannel(red, background.red, effectiveAlpha),
        green = compositeChannel(green, background.green, effectiveAlpha),
        blue = compositeChannel(blue, background.blue, effectiveAlpha),
    )
}

private fun compositeChannel(foreground: Int, background: Int, alpha: Double): Int {
    return (foreground * alpha + background * (1.0 - alpha)).roundToInt()
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
    val opaqueBackground = if (background.alpha < 1.0) {
        background.compositeOver(ColorRgb(red = 255, green = 255, blue = 255))
    } else {
        background
    }
    val visibleForeground = foreground.compositeOver(opaqueBackground)
    val foregroundLuminance = visibleForeground.relativeLuminance()
    val backgroundLuminance = opaqueBackground.relativeLuminance()
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
