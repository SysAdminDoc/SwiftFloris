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

package dev.patrickgold.florisboard.ime.media.emoji

import androidx.emoji2.text.EmojiCompat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * EmojiCompat evidence item EI5 — pins the androidx-emoji2 reflection target the
 * v1.8.173 `FlorisEmojiCompat` race fix depends on.
 *
 * The fix constructs `EmojiCompat` via its package-private `(Config)`
 * constructor (reflection) so the process-wide singleton stays null until
 * metadata load completes, avoiding the "Not initialized yet" crash. If a future
 * emoji2 bump changes that constructor's shape, the production code silently
 * falls back to `EmojiCompat.reset(config)` which reintroduces the race. This
 * test fails loudly in CI on exactly that drift, before it ships.
 */
class FlorisEmojiCompatReflectionGuardTest : FunSpec({

    test("EmojiCompat still exposes the (Config) constructor the reflection path depends on") {
        val ctor = EmojiCompat::class.java.getDeclaredConstructor(EmojiCompat.Config::class.java)
        isExpectedEmojiCompatConstructor(ctor) shouldBe true
    }

    test("shape guard rejects a wrong-arity or wrong-type constructor and null") {
        // No-arg constructor — wrong arity.
        isExpectedEmojiCompatConstructor(StringBuilder::class.java.getDeclaredConstructor()) shouldBe false
        // Single-arg constructor whose parameter is not EmojiCompat.Config.
        isExpectedEmojiCompatConstructor(
            StringBuilder::class.java.getDeclaredConstructor(String::class.java),
        ) shouldBe false
        isExpectedEmojiCompatConstructor(null) shouldBe false
    }
})
