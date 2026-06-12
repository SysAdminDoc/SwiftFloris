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

package dev.patrickgold.florisboard.ime.voice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class VoiceInputEmptyStateCopyTest : FunSpec({
    test("no-provider empty state explains supported offline providers and keeps install actions") {
        val stringsXml = locateStringsXml().readText()
        val summary = stringsXml.stringValue("settings__voice_input__status_futo_not_installed_summary")

        stringsXml.stringValue("settings__voice_input__status_futo_not_installed") shouldBe
            "Install FUTO Voice Input"
        summary shouldContain "FUTO Voice Input"
        summary shouldContain "WhisperInput"
        summary shouldContain "Whisper"
        summary shouldContain "SwiftFloris itself does not record audio"
        stringsXml.stringValue("voice_input_setup__install_fdroid") shouldBe "Install from F-Droid"
        stringsXml.stringValue("voice_input_setup__install_whisper") shouldBe "Install Whisper from F-Droid"
    }
})

private fun locateStringsXml(): File {
    return listOf(
        File("app/src/main/res/values/strings.xml"),
        File("src/main/res/values/strings.xml"),
    ).firstOrNull { it.isFile }
        ?: error("strings.xml not reachable from working directory ${File(".").absolutePath}")
}

private fun String.stringValue(name: String): String {
    val pattern = Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    return pattern.find(this)?.groupValues?.get(1)
        ?: error("Missing string resource $name")
}
