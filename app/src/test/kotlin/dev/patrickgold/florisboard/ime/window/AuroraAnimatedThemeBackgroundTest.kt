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

package dev.patrickgold.florisboard.ime.window

import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuroraAnimatedThemeBackgroundTest : FunSpec({
    test("Aurora animated background is gated to the bundled Aurora theme") {
        isAuroraAnimatedTheme(extCoreTheme(AuroraAnimatedThemeComponentId)) shouldBe true
        isAuroraAnimatedTheme(extCoreTheme("swiftkey_high_contrast")) shouldBe false
        isAuroraAnimatedTheme(
            ExtensionComponentName(
                extensionId = "external.theme.pack",
                componentId = AuroraAnimatedThemeComponentId,
            ),
        ) shouldBe false
    }
})
