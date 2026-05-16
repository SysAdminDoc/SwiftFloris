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

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the no-GMS / unavailable-config fallback path on [FlorisEmojiCompat.InstanceHandler].
 *
 * Exercises the path that triggers on AOSP-without-GMS, Huawei HMS-only, and other Play-Services-free devices where
 * [androidx.emoji2.text.DefaultEmojiCompatConfig.create] returns `null`. The instance handler must transition the
 * public load state to [EmojiCompatLoadState.Unavailable] in that case (instead of silently sitting at
 * [EmojiCompatLoadState.Loading] forever) so that consumers can render the fallback system-painter glyph path with a
 * definite signal rather than waiting on a callback that will never fire.
 */
class FlorisEmojiCompatTest : FunSpec({
    coroutineTestScope = true

    test("InstanceHandler transitions to Unavailable when the config provider returns null") {
        val handler = FlorisEmojiCompat.InstanceHandler(replaceAll = false, configProvider = { null })

        handler.loadStateFlow.test {
            awaitItem() shouldBe EmojiCompatLoadState.Loading
            handler.ensureLoad(backgroundScope)
            awaitItem() shouldBe EmojiCompatLoadState.Unavailable
        }
        handler.publishedInstanceFlow.value shouldBe null
    }

    test("InstanceHandler.ensureLoad is idempotent across repeated calls") {
        var providerCalls = 0
        val handler = FlorisEmojiCompat.InstanceHandler(replaceAll = false, configProvider = {
            providerCalls++
            null
        })

        handler.loadStateFlow.test {
            awaitItem() shouldBe EmojiCompatLoadState.Loading
            handler.ensureLoad(backgroundScope)
            handler.ensureLoad(backgroundScope)
            handler.ensureLoad(backgroundScope)
            awaitItem() shouldBe EmojiCompatLoadState.Unavailable
        }
        providerCalls shouldBe 1
    }

    test("InstanceHandler exposes Unavailable for the replaceAll branch too") {
        val handler = FlorisEmojiCompat.InstanceHandler(replaceAll = true, configProvider = { null })

        handler.loadStateFlow.test {
            awaitItem() shouldBe EmojiCompatLoadState.Loading
            handler.ensureLoad(backgroundScope)
            awaitItem() shouldBe EmojiCompatLoadState.Unavailable
        }
        handler.publishedInstanceFlow.value shouldBe null
    }
})
