/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.matchers.shouldBe
import org.junit.Test

class ClipboardSensitiveRevealPolicyTest {
    @Test
    fun onlySensitiveItemsCanBeRevealedOutsidePrivacyGates() {
        ClipboardSensitiveRevealPolicy.canReveal(
            isSensitive = true,
            isDeviceLocked = false,
            isIncognitoMode = false,
        ) shouldBe true
        ClipboardSensitiveRevealPolicy.canReveal(
            isSensitive = false,
            isDeviceLocked = false,
            isIncognitoMode = false,
        ) shouldBe false
    }

    @Test
    fun lockScreenAndIncognitoAlwaysDisableReveal() {
        ClipboardSensitiveRevealPolicy.canReveal(
            isSensitive = true,
            isDeviceLocked = true,
            isIncognitoMode = false,
        ) shouldBe false
        ClipboardSensitiveRevealPolicy.canReveal(
            isSensitive = true,
            isDeviceLocked = false,
            isIncognitoMode = true,
        ) shouldBe false
    }
}
