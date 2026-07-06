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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class QuickActionButtonAccessibilityTest : FunSpec({
    test("quick action buttons expose activation and disabled semantics") {
        val source = locateQuickActionButtonSource().readText()

        source shouldContain "onClick(label = actionA11yLabel)"
        source shouldContain "action.onPointerDown(context)"
        source shouldContain "action.onPointerUp(context)"
        source shouldContain "disabled()"
        source shouldContain "isActionActivatable"
    }
})

private fun locateQuickActionButtonSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionButton.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionButton.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("QuickActionButton.kt not reachable from working directory ${File(".").absolutePath}")
}
