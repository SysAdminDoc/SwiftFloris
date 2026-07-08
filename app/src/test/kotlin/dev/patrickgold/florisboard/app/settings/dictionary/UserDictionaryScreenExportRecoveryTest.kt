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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class UserDictionaryScreenExportRecoveryTest : FunSpec({
    test("encrypted export reports and reopens when recreation drops the passphrase") {
        val source = locateUserDictionarySource().readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        normalized shouldNotContain "if (uri == null || passphrase == null)"
        normalized shouldContain "if (uri == null) { passphrase?.fill('\\u0000') " +
            "return@rememberLauncherForActivityResult }"
        normalized shouldContain "if (passphrase == null) { encryptedExportDialogVisible = true " +
            "scope.launch { context.showLongToast(" +
            "R.string.settings__udm__encrypted_dictionary_export_passphrase_lost) } " +
            "return@rememberLauncherForActivityResult }"
    }
})

private fun locateUserDictionarySource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("UserDictionaryScreen.kt not reachable from working directory ${File(".").absolutePath}")
}
