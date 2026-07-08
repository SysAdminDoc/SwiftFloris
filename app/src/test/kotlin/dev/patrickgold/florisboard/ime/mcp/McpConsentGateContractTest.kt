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

package dev.patrickgold.florisboard.ime.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class McpConsentGateContractTest : FunSpec({

    test("IME startup gates MCP bridge binding on persisted consent") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt",
        ).readText()

        source shouldContain "bridgeEnabled = prefs.privacy.mcpConsent.get().allowsInvocation()"
    }

    test("MCP settings exposes an explicit bridge enable switch") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/mcp/McpSettingsScreen.kt",
        ).readText()
        val strings = locateProjectFile("app/src/main/res/values/strings.xml").readText()

        source shouldContain "val mcpConsent by prefs.privacy.mcpConsent.collectAsState()"
        source shouldContain "AddonConsentState.GRANTED"
        source shouldContain "AddonConsentState.DENIED"
        strings shouldContain "settings__mcp__status_disabled"
        strings shouldContain "settings__mcp__bridge_enabled"
    }
})

private fun locateProjectFile(path: String): File {
    var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (true) {
        val candidate = dir.resolve(path)
        if (candidate.isFile) return candidate
        dir = dir.parentFile ?: break
    }
    error("Could not locate project file: $path")
}
