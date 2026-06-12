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

package dev.patrickgold.florisboard.app.apptheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.florisboard.lib.color.neutralDynamicColorScheme

class SettingsSurfaceTintTest : FunSpec({
    test("light refined surfaces follow the selected accent instead of a fixed green palette") {
        val red = refinedScheme(primary = Color(0xFFF44336), isDark = false)
        val purple = refinedScheme(primary = Color(0xFF9C27B0), isDark = false)

        red.background.toArgb() shouldNotBe Color(0xFFFAFCF8).toArgb()
        red.background.toArgb() shouldNotBe purple.background.toArgb()
        red.surfaceContainer.toArgb() shouldNotBe purple.surfaceContainer.toArgb()
    }

    test("dark refined surfaces follow the selected accent instead of a fixed green palette") {
        val red = refinedScheme(primary = Color(0xFFF44336), isDark = true)
        val blue = refinedScheme(primary = Color(0xFF2196F3), isDark = true)

        red.background.toArgb() shouldNotBe Color(0xFF0B0F0D).toArgb()
        red.background.toArgb() shouldNotBe blue.background.toArgb()
        red.surfaceContainer.toArgb() shouldNotBe blue.surfaceContainer.toArgb()
    }

    test("amoled refined surfaces preserve the black base") {
        val scheme = refinedScheme(primary = Color(0xFF9C27B0), isDark = true, isAmoled = true)

        scheme.background.toArgb() shouldBe Color.Black.toArgb()
        scheme.surface.toArgb() shouldBe Color.Black.toArgb()
    }
})

private fun refinedScheme(
    primary: Color,
    isDark: Boolean,
    isAmoled: Boolean = false,
) = neutralDynamicColorScheme(
    primary = primary,
    isDark = isDark,
    isAmoled = isAmoled,
    contrastLevel = 0.18,
    modifyColorScheme = {
        it.refinedSurfaces(tint = primary, isDark = isDark, isAmoled = isAmoled)
    },
)
