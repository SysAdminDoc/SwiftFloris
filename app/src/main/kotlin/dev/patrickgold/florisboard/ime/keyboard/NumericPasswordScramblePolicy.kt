/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import java.security.SecureRandom
import java.util.Random

/** Builds the per-focus digit permutation used only by numeric password fields. */
internal object NumericPasswordScramblePolicy {
    fun shouldApply(
        enabled: Boolean,
        type: InputAttributes.Type,
        variation: InputAttributes.Variation,
    ): Boolean {
        return enabled && type == InputAttributes.Type.NUMBER && variation == InputAttributes.Variation.PASSWORD
    }

    fun newMapping(random: Random = SecureRandom()): Map<Int, Int> {
        val shuffled = (0..9).toMutableList()
        do {
            shuffled.shuffle(random)
        } while (shuffled.withIndex().all { (index, digit) -> index == digit })
        return shuffled.withIndex().associate { (digit, mappedDigit) -> digit to mappedDigit }
    }

    /** Returns the same Unicode digit set with [mappedDigit] substituted, or null for non-digits. */
    fun mappedDigitCode(code: Int, mappedDigit: Int): Int? {
        val digit = Character.digit(code, 10)
        return if (digit in 0..9 && mappedDigit in 0..9) {
            code - digit + mappedDigit
        } else {
            null
        }
    }

    fun remap(data: KeyData, mapping: Map<Int, Int>): KeyData {
        if (mapping.isEmpty() || data.type != dev.patrickgold.florisboard.ime.text.key.KeyType.NUMERIC) {
            return data
        }
        val digit = Character.digit(data.code, 10)
        val mappedDigit = mapping[digit] ?: return data
        val mappedCode = mappedDigitCode(data.code, mappedDigit) ?: return data
        return if (data is TextKeyData) {
            data.copy(
                code = mappedCode,
                label = String(Character.toChars(mappedCode)),
            )
        } else {
            data
        }
    }
}
