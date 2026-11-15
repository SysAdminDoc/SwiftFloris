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

import dev.patrickgold.florisboard.shouldWaitForMonitor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ObservableKeyboardStateTest : FunSpec({
    test("a batch edit publishes its changes once it ends") {
        val state = ObservableKeyboardState.new()
        state.batchEdit {
            it.isSelectionMode = true
            state.value.isSelectionMode shouldBe false
        }
        state.value.isSelectionMode shouldBe true
    }

    test("ending a batch edit publishes under the lock flag changes hold") {
        // Without it, a thread ending a batch can publish its snapshot after another thread
        // published a newer one, and observers keep the older state.
        val state = ObservableKeyboardState.new()
        state.beginBatchEdit()
        state.isSelectionMode = true
        shouldWaitForMonitor(state) { state.endBatchEdit() }
        state.value.rawValue shouldBe state.rawValue
        state.value.isSelectionMode shouldBe true
    }
})
