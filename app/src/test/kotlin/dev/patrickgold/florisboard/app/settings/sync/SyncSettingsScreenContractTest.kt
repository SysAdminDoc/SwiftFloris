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

package dev.patrickgold.florisboard.app.settings.sync

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

class SyncSettingsScreenContractTest : FunSpec({
    test("local folder sync file resolution stays off the click-thread") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt",
        ).readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        normalized shouldContain
            "suspend fun resolveLocalFolderSyncFileUri(channel: SyncChannel.LocalFolder, create: Boolean): Uri? " +
            "= withContext( Dispatchers.IO,"
        source shouldContain "return@withContext DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)"
        source shouldContain "if (!create) return@withContext null"
        source shouldContain "val savedManualExportTargetUri = manualExportTargetUri"
        source shouldContain "scope.launch {\n                            val target = when (channel)"
        source shouldContain "scope.launch {\n                                    val source = runCatching"
        source shouldContain "resolveLocalFolderSyncFileUri(channel, create = true)"
        source shouldContain "resolveLocalFolderSyncFileUri(channel, create = false)"
    }
})

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
