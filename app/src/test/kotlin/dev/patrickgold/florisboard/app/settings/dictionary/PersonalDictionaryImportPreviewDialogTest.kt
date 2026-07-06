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

package dev.patrickgold.florisboard.app.settings.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class PersonalDictionaryImportPreviewDialogTest : FunSpec({
    test("preview checkboxes expose one row-level activation target") {
        val source = locatePreviewDialogSource().readText()

        source shouldContain ".rippleClickable(role = Role.Checkbox)"
        Regex("""onCheckedChange = null""").findAll(source).count() shouldBe 2
        source shouldNotContain "onCheckedChange = onCheckedChange"
        source shouldNotContain "onCheckedChange = { skipFuturePreview = it }"
    }
})

private fun locatePreviewDialogSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/PersonalDictionaryImportPreviewDialog.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/PersonalDictionaryImportPreviewDialog.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("PersonalDictionaryImportPreviewDialog.kt not reachable from working directory ${File(".").absolutePath}")
}
