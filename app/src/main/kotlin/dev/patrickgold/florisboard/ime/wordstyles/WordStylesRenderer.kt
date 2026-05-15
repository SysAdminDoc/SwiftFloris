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

package dev.patrickgold.florisboard.ime.wordstyles

/**
 * ROADMAP §7 L12 — WordStyles renderer facade (FUTO v0.1.25 WordStyles
 * pattern).
 *
 * WordStyles is a small but loved FUTO feature: instead of inserting
 * plain Unicode text, the keyboard renders the typed run as a styled
 * **image** (custom font, colour, drop shadow, gradient) that the
 * focused editor receives as a PNG via `commitContent(InputContentInfoCompat)`.
 * Niche, but no competitor ships it on Android.
 *
 * This facade exposes the minimal surface the IME's smartbar
 * quick-action needs:
 *  - [renderStyledImage] converts a text+style pair into a PNG byte
 *    array suitable for `InputContentInfoCompat`.
 *  - [defaultStyles] surfaces the built-in styles a Settings UI shows
 *    (bold, gradient, neon, retro). Custom user-defined styles live in
 *    a SharedPreferences-backed store implemented alongside this
 *    facade once the renderer ships.
 *
 * The default implementation is a no-op (returns null) — the actual
 * Canvas/Paint render lives in `WordStylesAndroidRenderer` (deferred
 * to L12.1 because it pulls Android-only `android.graphics.*` imports
 * that we keep out of the cross-cutting facade tier).
 *
 * A separate **L12 streaming-voice** sub-item (WhisperInput-style
 * hold-mic-to-talk) is already covered by Next-2.4 which shipped
 * `StreamingVoiceTranscriptBuffer.consumeStreamingChunk(...)` in
 * v1.7.9.
 */
interface WordStylesRenderer {
    /**
     * Render [text] using [style] into a PNG byte array; return null
     * when the renderer cannot satisfy the request (e.g. the bundled
     * font is missing or the platform is below the SDK floor).
     */
    fun renderStyledImage(text: String, style: WordStyle): ByteArray?

    val defaultStyles: List<WordStyle>

    object Default : WordStylesRenderer {
        override fun renderStyledImage(text: String, style: WordStyle): ByteArray? = null
        override val defaultStyles: List<WordStyle> = WordStyle.BuiltIns
    }
}

/**
 * One render configuration. Sized to fit the [InputContentInfoCompat]
 * 1 MB / 800×400 sweet spot most editors accept comfortably.
 */
data class WordStyle(
    val id: String,
    val displayName: String,
    /** Hex foreground colour, `#RRGGBBAA` (alpha last). */
    val foregroundColor: String,
    /** Hex background colour, or transparent (`#00000000`). */
    val backgroundColor: String,
    /** Font family name as known to Android's [Typeface]. */
    val fontFamily: String,
    /** Font size in sp. */
    val fontSizeSp: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    /** Hex gradient end colour; when set, the renderer fills the
     *  foreground with a linear gradient `foregroundColor → gradientEnd`. */
    val gradientEnd: String? = null,
    /** Drop-shadow radius in dp; 0 disables the shadow pass. */
    val shadowRadiusDp: Int = 0,
    /** Padding around the text in dp. */
    val paddingDp: Int = 16,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(foregroundColor.matches(HEX_REGEX)) {
            "foregroundColor must be #RRGGBBAA hex; was $foregroundColor"
        }
        require(backgroundColor.matches(HEX_REGEX)) {
            "backgroundColor must be #RRGGBBAA hex; was $backgroundColor"
        }
        gradientEnd?.let {
            require(it.matches(HEX_REGEX)) {
                "gradientEnd must be #RRGGBBAA hex; was $it"
            }
        }
        require(fontSizeSp in 8..240) { "fontSizeSp must be in 8..240; was $fontSizeSp" }
        require(shadowRadiusDp in 0..32) {
            "shadowRadiusDp must be in 0..32; was $shadowRadiusDp"
        }
        require(paddingDp in 0..96) { "paddingDp must be in 0..96; was $paddingDp" }
    }

    companion object {
        private val HEX_REGEX = Regex("^#[0-9a-fA-F]{8}$")

        /** Four canonical WordStyles shipped out of the box. */
        val BuiltIns: List<WordStyle> = listOf(
            WordStyle(
                id = "neon",
                displayName = "Neon",
                foregroundColor = "#39FF14FF",
                backgroundColor = "#101216FF",
                fontFamily = "sans-serif-medium",
                fontSizeSp = 64,
                isBold = true,
                shadowRadiusDp = 8,
            ),
            WordStyle(
                id = "gradient_sunset",
                displayName = "Gradient Sunset",
                foregroundColor = "#FF4F8AFF",
                backgroundColor = "#00000000",
                fontFamily = "sans-serif",
                fontSizeSp = 56,
                isBold = true,
                gradientEnd = "#FFD24FFF",
            ),
            WordStyle(
                id = "retro_typewriter",
                displayName = "Retro Typewriter",
                foregroundColor = "#1E1E1EFF",
                backgroundColor = "#F5F1E8FF",
                fontFamily = "monospace",
                fontSizeSp = 44,
                isBold = false,
                isItalic = false,
            ),
            WordStyle(
                id = "soft_pastel",
                displayName = "Soft Pastel",
                foregroundColor = "#5E6B78FF",
                backgroundColor = "#F8E1E7FF",
                fontFamily = "sans-serif-light",
                fontSizeSp = 48,
                isBold = false,
                shadowRadiusDp = 2,
            ),
        )
    }
}

object WordStylesRendererRegistry {
    @Volatile
    private var current: WordStylesRenderer = WordStylesRenderer.Default
    val active: WordStylesRenderer get() = current
    fun setActive(renderer: WordStylesRenderer) { current = renderer }
    fun reset() { current = WordStylesRenderer.Default }
}
