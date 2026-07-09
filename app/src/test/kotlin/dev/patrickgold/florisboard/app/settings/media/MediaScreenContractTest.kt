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

package dev.patrickgold.florisboard.app.settings.media

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class MediaScreenContractTest : FunSpec({
    test("imported sticker folder is saved only after Android grants durable access") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/media/MediaScreen.kt",
        ).readText()

        source shouldContain "val grantResult = runCatching"
        source shouldContain "context.contentResolver.takePersistableUriPermission(uri, grantFlags)"
        source shouldContain "if (grantResult.isFailure)"
        source shouldContain "return@rememberLauncherForActivityResult"
        source shouldContain "prefs.sticker.userFolderUri.set(uri.toString())"
    }
})

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
