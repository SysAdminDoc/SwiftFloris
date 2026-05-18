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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import java.io.ByteArrayOutputStream

/**
 * ROADMAP §7 L12.1 — Android Canvas implementation of [WordStylesRenderer].
 *
 * Backs the WordStyles facade (L12) with a real `android.graphics.*`
 * render path: rasterises text using `Paint` + `Canvas`, supports
 * background fill, foreground colour, linear gradient, shadow layer,
 * and the configurable padding/font-size declared by [WordStyle].
 * Encodes the resulting bitmap to PNG bytes ready for
 * `InputContentInfoCompat`.
 *
 * Lives in `:app` (not in a sub-module) because the entire surface
 * is `android.graphics.*`; the rest of L12's API stays cross-cutting
 * inside `WordStylesRenderer`.
 *
 * Registered via [WordStylesRendererRegistry.setActive] at IME boot
 * by the application module's `FlorisApplication` so the smartbar
 * quick-action sees a working renderer immediately — no addon needed.
 */
class WordStylesCanvasRenderer(private val context: Context) : WordStylesRenderer {

    override fun renderStyledImage(text: String, style: WordStyle): ByteArray? {
        if (text.isEmpty()) return null
        return runCatching {
            val paint = paintFor(style)
            val metrics = paint.fontMetrics
            val textWidth = paint.measureText(text)
            val padding = dp(style.paddingDp)
            val width = (textWidth + padding * 2).toInt().coerceAtLeast(1)
            val height = (metrics.descent - metrics.ascent + padding * 2).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(parseHex(style.backgroundColor))
            // Gradient via LinearGradient when configured.
            style.gradientEnd?.let { endHex ->
                paint.shader = LinearGradient(
                    /* x0 = */ 0f,
                    /* y0 = */ 0f,
                    /* x1 = */ width.toFloat(),
                    /* y1 = */ 0f,
                    /* color0 = */ parseHex(style.foregroundColor),
                    /* color1 = */ parseHex(endHex),
                    /* tile = */ Shader.TileMode.CLAMP,
                )
            }
            // Shadow.
            if (style.shadowRadiusDp > 0) {
                paint.setShadowLayer(
                    /* radius = */ dp(style.shadowRadiusDp),
                    /* dx = */ 0f,
                    /* dy = */ dp(2),
                    /* shadowColor = */ Color.argb(128, 0, 0, 0),
                )
            }
            val baseline = padding - metrics.ascent
            canvas.drawText(text, padding, baseline, paint)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, /* quality = */ 100, baos)
            bitmap.recycle()
            baos.toByteArray()
        }.getOrNull()
    }

    override val defaultStyles: List<WordStyle> = WordStyle.BuiltIns

    private fun paintFor(style: WordStyle): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = parseHex(style.foregroundColor)
        paint.textSize = sp(style.fontSizeSp)
        val baseTypeface = Typeface.create(style.fontFamily, Typeface.NORMAL)
        val typefaceStyle = when {
            style.isBold && style.isItalic -> Typeface.BOLD_ITALIC
            style.isBold -> Typeface.BOLD
            style.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        paint.typeface = Typeface.create(baseTypeface, typefaceStyle)
        return paint
    }

    private fun dp(value: Int): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        )

    private fun sp(value: Int): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value.toFloat(),
            context.resources.displayMetrics,
        )

    /** Parse `#RRGGBBAA` (alpha last, per [WordStyle]) to an ARGB int. */
    private fun parseHex(hex: String): Int {
        val r = hex.substring(1, 3).toInt(16)
        val g = hex.substring(3, 5).toInt(16)
        val b = hex.substring(5, 7).toInt(16)
        val a = hex.substring(7, 9).toInt(16)
        return Color.argb(a, r, g, b)
    }
}
