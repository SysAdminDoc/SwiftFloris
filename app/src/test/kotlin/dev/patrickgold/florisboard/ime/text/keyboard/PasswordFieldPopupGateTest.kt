/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PasswordFieldPopupGateTest : FunSpec({
    test("PASSWORD variation suppresses popups") {
        PasswordFieldPopupGate.shouldSuppressPopups(KeyVariation.PASSWORD) shouldBe true
    }

    test("NORMAL variation does not suppress popups") {
        PasswordFieldPopupGate.shouldSuppressPopups(KeyVariation.NORMAL) shouldBe false
    }

    test("ALL variation does not suppress popups (default for unspecified fields)") {
        PasswordFieldPopupGate.shouldSuppressPopups(KeyVariation.ALL) shouldBe false
    }

    test("EMAIL_ADDRESS variation does not suppress popups") {
        PasswordFieldPopupGate.shouldSuppressPopups(KeyVariation.EMAIL_ADDRESS) shouldBe false
    }

    test("URI variation does not suppress popups") {
        PasswordFieldPopupGate.shouldSuppressPopups(KeyVariation.URI) shouldBe false
    }

    test("contract holds across every KeyVariation entry — only PASSWORD trips") {
        // Forward-compat guard: if a future variation is added to the enum the test reminds
        // the author to decide whether the new variation should be a password equivalent.
        val tripped = KeyVariation.entries.filter { PasswordFieldPopupGate.shouldSuppressPopups(it) }
        tripped shouldBe listOf(KeyVariation.PASSWORD)
    }
})
