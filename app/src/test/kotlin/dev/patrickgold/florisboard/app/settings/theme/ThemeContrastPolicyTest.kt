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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.florisboard.lib.snygg.value.SnyggDefinedVarValue
import org.florisboard.lib.snygg.value.SnyggStaticColorValue

class ThemeContrastPolicyTest : FunSpec({
    test("warns when an editable foreground falls below WCAG AA") {
        val warning = themeContrastWarning(
            propertyName = "foreground",
            propertyValue = staticColor(0x77),
            siblingProperties = mapOf("background" to staticColor(0xff)),
            definedVariables = emptyMap(),
        )

        warning shouldNotBe null
        val contrastRatio = warning?.contrastRatio ?: Double.POSITIVE_INFINITY
        (contrastRatio < ThemeTextContrastMinimum) shouldBe true
    }

    test("resolves defined variables and accepts a passing pair") {
        val result = themeContrastWarning(
            propertyName = "foreground",
            propertyValue = SnyggDefinedVarValue("--text"),
            siblingProperties = mapOf("background" to SnyggDefinedVarValue("--surface")),
            definedVariables = mapOf(
                "--text" to staticColor(0x76),
                "--surface" to staticColor(0xff),
            ),
        )

        result shouldBe null
    }

    test("accounts for foreground alpha when evaluating contrast") {
        val warning = themeContrastWarning(
            propertyName = "foreground",
            propertyValue = SnyggStaticColorValue(Color(1f, 1f, 1f, 0.25f)),
            siblingProperties = mapOf("background" to staticColor(0x00)),
            definedVariables = emptyMap(),
        )

        warning shouldNotBe null
    }

    test("does not warn for dynamic or incomplete pairs") {
        themeContrastWarning(
            propertyName = "foreground",
            propertyValue = SnyggDefinedVarValue("--dynamic"),
            siblingProperties = emptyMap(),
            definedVariables = emptyMap(),
        ) shouldBe null

        themeContrastWarning(
            propertyName = "font-size",
            propertyValue = staticColor(0x00),
            siblingProperties = emptyMap(),
            definedVariables = emptyMap(),
        ) shouldBe null
    }
})

private fun staticColor(channel: Int): SnyggStaticColorValue {
    val value = channel / 255f
    return SnyggStaticColorValue(Color(value, value, value))
}
