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

package dev.patrickgold.florisboard.ime.media.sticker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object StickerRenderer {
    private const val Size = 512
    private const val CornerRadius = 96f

    fun renderPng(sticker: Sticker, file: File) {
        file.parentFile?.mkdirs()
        val bitmap = render(sticker)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }

    private fun render(sticker: Sticker): Bitmap {
        val bitmap = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = sticker.backgroundColor
        canvas.drawRoundRect(RectF(24f, 24f, 488f, 488f), CornerRadius, CornerRadius, paint)

        paint.color = sticker.accentColor
        canvas.drawCircle(420f, 92f, 42f, paint)
        paint.alpha = 54
        canvas.drawCircle(92f, 424f, 96f, paint)
        paint.alpha = 255

        paint.color = sticker.textColor
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 158f
        canvas.drawCenteredText(sticker.emoji, centerX = 256f, centerY = 212f, paint)

        paint.textSize = fitTextSize(sticker.label, paint, maxWidth = 380f, startSize = 64f)
        canvas.drawCenteredText(sticker.label.uppercase(Locale.ROOT), centerX = 256f, centerY = 365f, paint)

        return bitmap
    }

    private fun fitTextSize(text: String, paint: Paint, maxWidth: Float, startSize: Float): Float {
        var size = startSize
        while (size > 30f) {
            paint.textSize = size
            if (paint.measureText(text.uppercase(Locale.ROOT)) <= maxWidth) break
            size -= 2f
        }
        return size
    }

    private fun Canvas.drawCenteredText(text: String, centerX: Float, centerY: Float, paint: Paint) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        drawText(text, centerX, centerY - bounds.exactCenterY(), paint)
    }
}
