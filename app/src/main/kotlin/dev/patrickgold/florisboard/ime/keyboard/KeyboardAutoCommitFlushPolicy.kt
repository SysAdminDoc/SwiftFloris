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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.text.key.KeyType

internal object KeyboardAutoCommitFlushPolicy {
    fun shouldFlushBeforeCommit(
        imeUiMode: ImeUiMode,
        keyboardMode: KeyboardMode,
        keyType: KeyType,
        text: String,
        isFirstCodePointAlphabetic: Boolean = text.firstCodePointIsAlphabetic(),
    ): Boolean {
        if (text.isEmpty()) return false
        if (imeUiMode == ImeUiMode.MEDIA) return true
        if (keyboardMode.isNumericOrPhoneMode()) return false
        if (keyType != KeyType.CHARACTER && keyType != KeyType.NUMERIC) return false
        return !isFirstCodePointAlphabetic
    }

    private fun KeyboardMode.isNumericOrPhoneMode(): Boolean {
        return this == KeyboardMode.NUMERIC ||
            this == KeyboardMode.NUMERIC_ADVANCED ||
            this == KeyboardMode.PHONE ||
            this == KeyboardMode.PHONE2
    }

    private fun String.firstCodePointIsAlphabetic(): Boolean {
        return isNotEmpty() && Character.isAlphabetic(codePointAt(0))
    }
}
