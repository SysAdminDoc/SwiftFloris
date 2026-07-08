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

class ExtensionExportScreenContractTest : FunSpec({
    test("extension export launches once from an effect and writes off main") {
        val source = locateExtensionExportSource().readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        source shouldContain "import androidx.compose.runtime.LaunchedEffect"
        source shouldContain "import androidx.compose.runtime.saveable.rememberSaveable"
        normalized shouldContain
            "var pickerLaunchRequested by rememberSaveable(ext.meta.id) { mutableStateOf(false) }"
        normalized shouldContain "LaunchedEffect(Unit) { if (!pickerLaunchRequested) { " +
            "pickerLaunchRequested = true runCatching { " +
            "exportLauncher.launch(ExtensionDefaults.createFlexName(ext.meta.id)) }"
        normalized shouldContain "scope.launch { val exportResult = runCatching { " +
            "withContext(Dispatchers.IO) { extensionManager.export(ext, uri) } }"

        normalized shouldNotContain "content { exportLauncher.launch("
        normalized shouldNotContain "val exportResult = runCatching { extensionManager.export(ext, uri) }"
    }
})

private fun locateExtensionExportSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/app/ext/ExtensionExportScreen.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/app/ext/ExtensionExportScreen.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("ExtensionExportScreen.kt not reachable from working directory ${File(".").absolutePath}")
}
