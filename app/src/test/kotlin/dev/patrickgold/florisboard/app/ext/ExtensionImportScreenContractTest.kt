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

package dev.patrickgold.florisboard.app.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ExtensionImportScreenContractTest : FunSpec({
    test("import review summary uses one translatable sentence") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/ext/ExtensionImportScreen.kt",
        ).readText()
        val strings = locateProjectFile("app/src/main/res/values/strings.xml").readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        normalized shouldContain "secondaryText = stringRes( R.string.ext__import__review_summary,"
        normalized shouldContain "\"new_files\" to pluralsRes("
        normalized shouldContain "\"updates\" to pluralsRes("
        normalized shouldContain "\"skipped_files\" to pluralsRes("
        normalized shouldNotContain "append(\", \")"
        normalized shouldNotContain "append(\", and \")"

        strings shouldContain "name=\"ext__import__review_summary\""
        strings shouldContain "{new_files}, {updates}, and {skipped_files}"
        strings shouldNotContain "ext__import__review_message_with_actions_suffix"
    }
})

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
