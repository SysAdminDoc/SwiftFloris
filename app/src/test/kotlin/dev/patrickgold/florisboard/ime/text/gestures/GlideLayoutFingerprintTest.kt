/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.gestures

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GlideLayoutFingerprintTest : FunSpec({
    test("matches unchanged same-object layouts") {
        val keyViews = arrayListOf(
            key("a", left = 0f, top = 0f, right = 10f, bottom = 10f),
            key("b", left = 10f, top = 0f, right = 20f, bottom = 10f),
        )
        val cachedKeys = ArrayList(keyViews)
        val fingerprint = GlideLayoutFingerprint.from(keyViews)

        GlideLayoutFingerprint.matches(
            layoutSubtype = Subtype.DEFAULT,
            subtype = Subtype.DEFAULT,
            currentKeys = cachedKeys,
            newKeys = keyViews,
            currentFingerprint = fingerprint,
            newFingerprint = GlideLayoutFingerprint.from(keyViews),
        ) shouldBe true
    }

    test("detects in-place relayout of the same TextKey objects") {
        val keyViews = arrayListOf(
            key("a", left = 0f, top = 0f, right = 10f, bottom = 10f),
            key("b", left = 10f, top = 0f, right = 20f, bottom = 10f),
        )
        val cachedKeys = ArrayList(keyViews)
        val before = GlideLayoutFingerprint.from(keyViews)

        keyViews[0].visibleBounds.apply {
            left = 2f
            right = 12f
        }
        val after = GlideLayoutFingerprint.from(keyViews)

        cachedKeys shouldBe keyViews
        after shouldNotBe before
        GlideLayoutFingerprint.matches(
            layoutSubtype = Subtype.DEFAULT,
            subtype = Subtype.DEFAULT,
            currentKeys = cachedKeys,
            newKeys = keyViews,
            currentFingerprint = before,
            newFingerprint = after,
        ) shouldBe false
    }
})

private fun key(label: String, left: Float, top: Float, right: Float, bottom: Float): TextKey {
    return TextKey(TextKeyData(code = label.first().code, label = label)).also { key ->
        key.visibleBounds.apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }
}
