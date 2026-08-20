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

import android.text.InputType
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.Random

class NumericPasswordScramblePolicyTest {
    @Test
    fun mappingIsAStableNonIdentityPermutation() {
        val mapping = NumericPasswordScramblePolicy.newMapping(Random(7))

        mapping.keys shouldContainExactlyInAnyOrder (0..9).toList()
        mapping.values shouldContainExactlyInAnyOrder (0..9).toList()
        mapping shouldBe mapping
        mapping.any { (digit, mappedDigit) -> digit != mappedDigit } shouldBe true
    }

    @Test
    fun scrambleAppliesOnlyToNumericPasswordVariation() {
        NumericPasswordScramblePolicy.shouldApply(
            enabled = true,
            type = InputAttributes.wrap(InputType.TYPE_CLASS_NUMBER).type,
            variation = InputAttributes.wrap(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ).variation,
        ) shouldBe true
        NumericPasswordScramblePolicy.shouldApply(
            enabled = true,
            type = InputAttributes.wrap(InputType.TYPE_CLASS_NUMBER).type,
            variation = InputAttributes.Variation.NORMAL,
        ) shouldBe false
        NumericPasswordScramblePolicy.shouldApply(
            enabled = true,
            type = InputAttributes.Type.PHONE,
            variation = InputAttributes.Variation.PASSWORD,
        ) shouldBe false
        NumericPasswordScramblePolicy.shouldApply(
            enabled = false,
            type = InputAttributes.Type.NUMBER,
            variation = InputAttributes.Variation.PASSWORD,
        ) shouldBe false
    }

    @Test
    fun remapKeepsTheDigitScriptAndLeavesNonNumericKeysUntouched() {
        val mapping = (0..9).associateWith { (it + 3) % 10 }
        val arabicOne = '\u0661'.code
        val arabicNine = '\u0669'.code

        NumericPasswordScramblePolicy.mappedDigitCode(arabicOne, 9) shouldBe arabicNine
        NumericPasswordScramblePolicy.mappedDigitCode('+'.code, 9) shouldBe null

        val numeric = TextKeyData(type = KeyType.NUMERIC, code = '1'.code, label = "1")
        val remapped = NumericPasswordScramblePolicy.remap(numeric, mapping)
        remapped.code shouldBe '4'.code
        remapped.label shouldBe "4"

        val letter = TextKeyData(code = 'a'.code, label = "a")
        NumericPasswordScramblePolicy.remap(letter, mapping) shouldBe letter
    }
}
