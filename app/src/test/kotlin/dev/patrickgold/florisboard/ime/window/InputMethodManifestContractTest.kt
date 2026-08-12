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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class InputMethodManifestContractTest : FunSpec({
    test("IME manifest advertises stylus handwriting and touch-exploration inline autofill") {
        val manifest = locateProjectFile("app/src/main/res/xml/method.xml").readText()

        manifest shouldContain "android:supportsStylusHandwriting=\"true\""
        manifest shouldContain "android:supportsInlineSuggestionsWithTouchExploration=\"true\""
        manifest shouldContain "tools:targetApi=\"tiramisu\""
    }
})

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
