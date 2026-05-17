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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.window.ImeWindowConstraints
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import dev.patrickgold.florisboard.ime.window.ImeWindowSpec

internal object TextKeyboardSplitLayout {
    private const val MaxGutterWidthFraction = 0.35f

    fun gutterPx(
        keyboardMode: KeyboardMode,
        windowSpec: ImeWindowSpec,
        defaultGutterPx: Float,
        keyboardWidthPx: Float,
    ): Float {
        val fixedSpec = windowSpec as? ImeWindowSpec.Fixed ?: return 0f
        val splitConstraints = fixedSpec.constraints as? ImeWindowConstraints.Fixed.Split ?: return 0f
        return gutterPx(
            keyboardMode = keyboardMode,
            fixedMode = fixedSpec.fixedMode,
            splitViable = splitConstraints.isViable,
            defaultGutterPx = defaultGutterPx,
            keyboardWidthPx = keyboardWidthPx,
        )
    }

    fun gutterPx(
        keyboardMode: KeyboardMode,
        fixedMode: ImeWindowMode.Fixed,
        splitViable: Boolean,
        defaultGutterPx: Float,
        keyboardWidthPx: Float,
    ): Float {
        if (keyboardMode != KeyboardMode.CHARACTERS) return 0f
        if (fixedMode != ImeWindowMode.Fixed.SPLIT || !splitViable) return 0f
        if (!defaultGutterPx.isFinite() || defaultGutterPx <= 0f) return 0f
        if (!keyboardWidthPx.isFinite() || keyboardWidthPx <= 0f) return 0f
        return defaultGutterPx.coerceIn(0f, keyboardWidthPx * MaxGutterWidthFraction)
    }

    fun layoutWidthPx(keyboardWidthPx: Float, gutterPx: Float): Float {
        if (!keyboardWidthPx.isFinite() || keyboardWidthPx <= 0f) return 0f
        if (!gutterPx.isFinite() || gutterPx <= 0f) return keyboardWidthPx
        return (keyboardWidthPx - gutterPx).coerceAtLeast(0f)
    }
}
