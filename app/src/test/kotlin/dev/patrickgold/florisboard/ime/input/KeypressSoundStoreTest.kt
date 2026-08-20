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

package dev.patrickgold.florisboard.ime.input

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KeypressSoundStoreTest {
    @Test
    fun keyCodesResolveToTheSameClassesAsSystemEffects() {
        KeypressSoundClass.fromKeyCode(KeyCode.DELETE) shouldBe KeypressSoundClass.DELETE
        KeypressSoundClass.fromKeyCode(KeyCode.ENTER) shouldBe KeypressSoundClass.RETURN
        KeypressSoundClass.fromKeyCode(KeyCode.SPACE) shouldBe KeypressSoundClass.SPACEBAR
        KeypressSoundClass.fromKeyCode(KeyCode.CJK_SPACE) shouldBe KeypressSoundClass.STANDARD
        KeypressSoundClass.fromKeyCode('a'.code) shouldBe KeypressSoundClass.STANDARD
    }

    @Test
    fun everyClassHasAnIndependentStableFileName() {
        KeypressSoundClass.entries.map { it.fileName }
            .shouldContainExactlyInAnyOrder(
                "standard.sound",
                "delete.sound",
                "return.sound",
                "spacebar.sound",
            )
    }

    @Test
    fun boundedCopyKeepsTheExactLimit() {
        val source = ByteArray(KeypressSoundStore.MaxSoundBytes.toInt()) { 7 }
        val output = ByteArrayOutputStream()

        KeypressSoundStore.copyBounded(
            input = ByteArrayInputStream(source),
            output = output,
            limit = KeypressSoundStore.MaxSoundBytes,
        ) shouldBe KeypressSoundStore.MaxSoundBytes
        output.size() shouldBe source.size
    }

    @Test
    fun boundedCopyRejectsAnOversizedSourceBeforePublishingIt() {
        val source = ByteArray(KeypressSoundStore.MaxSoundBytes.toInt() + 1) { 7 }

        shouldThrow<IllegalArgumentException> {
            KeypressSoundStore.copyBounded(
                input = ByteArrayInputStream(source),
                output = ByteArrayOutputStream(),
                limit = KeypressSoundStore.MaxSoundBytes,
            )
        }
    }
}
