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

package dev.patrickgold.florisboard.ime.bidi

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.util.concurrent.atomic.AtomicReference

/**
 * ROADMAP §7 L4.2 — Noto Nastaliq Urdu font provider.
 *
 * Urdu users expect Nastaliq positional shaping. Standard Android
 * fallback fonts render Urdu in **Naskh** (Arabic-style) which is the
 * Urdu equivalent of typing English in Cyrillic — technically readable
 * but visually wrong.
 *
 * SwiftFloris bundles Noto Nastaliq Urdu (OFL-1.1; Apache-compatible
 * with attribution in `app/src/main/assets/fonts/LICENSE-OFL.txt`) at
 * [BUNDLED_FONT_PATH]. This provider lazily loads it via
 * `Typeface.createFromAsset` on first access. The keyboard renderer
 * routes Urdu-subtype Arabic-script labels through [bundledFontFamily]
 * to render in correct Nastaliq shape.
 *
 * If the asset is missing (e.g. custom build without the font file),
 * the provider falls back to `Typeface.DEFAULT` so the IME still
 * renders. Bundle source and attribution live in `docs/FONTS.md`.
 */
object NastaliqFontProvider {

    /** Asset-relative path to the bundled font. */
    const val BUNDLED_FONT_PATH: String = "fonts/NotoNastaliqUrdu-Regular.ttf"

    private val cached = AtomicReference<Typeface?>(null)

    /**
     * Lazily-loaded Nastaliq typeface. Returns `Typeface.DEFAULT` when
     * the bundled asset is missing or fails to load (graceful fallback
     * — Urdu still renders, just in Naskh).
     */
    fun bundledTypeface(context: Context): Typeface {
        cached.get()?.let { return it }
        return try {
            val loaded = Typeface.createFromAsset(
                context.applicationContext.assets,
                BUNDLED_FONT_PATH,
            )
            cached.compareAndSet(null, loaded)
            loaded
        } catch (e: Throwable) {
            flogError {
                "NastaliqFontProvider: failed to load $BUNDLED_FONT_PATH — " +
                    "Urdu will render in Naskh fallback. $e"
            }
            val fallback = Typeface.DEFAULT
            cached.compareAndSet(null, fallback)
            fallback
        }
    }

    /** Compose font-family wrapper around [bundledTypeface]. */
    fun bundledFontFamily(context: Context): FontFamily {
        return FontFamily(bundledTypeface(context))
    }

    /** True when the active subtype language should prefer Nastaliq glyphs. */
    fun isUrduLanguage(language: String): Boolean {
        return language.equals("ur", ignoreCase = true)
    }

    /** True when [text] should use the bundled Nastaliq font for an Urdu subtype. */
    fun shouldRouteText(language: String, text: String?): Boolean {
        if (!isUrduLanguage(language) || text.isNullOrBlank()) return false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isArabicScriptCodePoint(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    /** True when the bundled asset is loadable. */
    fun isAvailable(context: Context): Boolean {
        return try {
            context.applicationContext.assets.openFd(BUNDLED_FONT_PATH).use { true }
        } catch (_: Throwable) {
            false
        }
    }

    /** Test-only: clears the cached Typeface so a fresh load happens. */
    internal fun resetForTesting() {
        cached.set(null)
    }

    private fun isArabicScriptCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x0600..0x06FF ||
            codePoint in 0x0750..0x077F ||
            codePoint in 0x0870..0x08FF ||
            codePoint in 0xFB50..0xFDFF ||
            codePoint in 0xFE70..0xFEFF
    }
}
