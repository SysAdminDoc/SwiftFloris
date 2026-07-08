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

import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class InputEventDispatcherTest : FunSpec({
    test("send down and up updates pressed state synchronously") {
        val dispatcher = InputEventDispatcher.new()
        val receiver = RecordingReceiver()
        dispatcher.keyEventReceiver = receiver

        dispatcher.sendDown(TextKeyData.DELETE)
        dispatcher.isPressed(TextKeyData.DELETE.code) shouldBe true
        dispatcher.isAnyPressed() shouldBe true

        dispatcher.sendUp(TextKeyData.DELETE)
        dispatcher.isPressed(TextKeyData.DELETE.code) shouldBe false
        dispatcher.isAnyPressed() shouldBe false

        receiver.events shouldContainExactly listOf(
            "down:${TextKeyData.DELETE.code}",
            "up:${TextKeyData.DELETE.code}",
        )

        dispatcher.close()
    }

    test("send down up dispatches a complete key stroke synchronously") {
        val dispatcher = InputEventDispatcher.new()
        val receiver = RecordingReceiver()
        dispatcher.keyEventReceiver = receiver

        dispatcher.sendDownUp(TextKeyData.SPACE)
        dispatcher.isAnyPressed() shouldBe false

        receiver.events shouldContainExactly listOf(
            "down:${TextKeyData.SPACE.code}",
            "up:${TextKeyData.SPACE.code}",
        )

        dispatcher.close()
    }

    test("blockUp is published inside the Main action before repeat dispatch") {
        val source = locateInputEventDispatcherSource().readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        normalized shouldContain "val longPressResult = withContext(Dispatchers.Main) { " +
            "val handled = onLongPress() if (handled) { pressedKeyInfo.blockUp = true } handled }"
        normalized shouldContain "withContext(Dispatchers.Main) { val proceed = onRepeat() if (proceed) { " +
            "pressedKeyInfo.blockUp = true keyEventReceiver?.onInputKeyRepeat(repeatData) } }"
        normalized shouldNotContain "if (longPressResult) { pressedKeyInfo.blockUp = true }"
        normalized shouldNotContain "if (onRepeatResult) { pressedKeyInfo.blockUp = true }"
    }
})

private fun locateInputEventDispatcherSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/input/InputEventDispatcher.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/input/InputEventDispatcher.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("InputEventDispatcher.kt not reachable from working directory ${File(".").absolutePath}")
}

private class RecordingReceiver : InputKeyEventReceiver {
    val events = mutableListOf<String>()

    override fun onInputKeyDown(data: KeyData) {
        events.add("down:${data.code}")
    }

    override fun onInputKeyUp(data: KeyData) {
        events.add("up:${data.code}")
    }

    override fun onInputKeyRepeat(data: KeyData) {
        events.add("repeat:${data.code}")
    }

    override fun onInputKeyCancel(data: KeyData) {
        events.add("cancel:${data.code}")
    }
}
