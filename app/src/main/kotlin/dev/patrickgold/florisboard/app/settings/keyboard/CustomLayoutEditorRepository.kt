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

package dev.patrickgold.florisboard.app.settings.keyboard

import android.content.Context
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangement
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.ext.ExtensionDefaults
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.io.FlorisRef
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.writeJson

internal class CustomLayoutEditorRepository(
    private val context: Context,
    private val extensionManager: ExtensionManager,
) {
    suspend fun loadArrangement(
        componentName: ExtensionComponentName,
        component: LayoutArrangementComponent,
    ): Result<LayoutArrangement> = withContext(Dispatchers.IO) {
        runCatching {
            val extension = extensionManager.getExtensionById(componentName.extensionId)
                ?: error("Extension ${componentName.extensionId} is not indexed.")
            val json = ZipUtils.readFileFromArchive(
                context = context,
                zipRef = requireNotNull(extension.sourceRef) { "Extension has no source archive." },
                relPath = component.arrangementFile(LayoutType.CHARACTERS),
            ).getOrThrow()
            loadJsonAsset<LayoutArrangement>(json).getOrThrow()
        }
    }

    suspend fun saveLocalLayout(
        draft: CustomLayoutEditorDraft,
        existingComponentIds: Set<String>,
    ): Result<ExtensionComponentName> = withContext(Dispatchers.IO) {
        runCatching {
            val validation = CustomLayoutEditorPolicy.validate(draft, existingComponentIds)
            require(validation.isValid) { "Layout contains invalid edits: ${validation.errors.joinToString()}" }

            val extension = CustomLayoutEditorPolicy.buildKeyboardExtension(draft)
            val archiveName = ExtensionDefaults.createFlexName(extension.meta.id)
            val archiveRef = FlorisRef.internal(ExtensionManager.IME_KEYBOARD_PATH).subRef(archiveName)
            val workspace = FsDir(context.cacheDir, "custom-layout-editor")
            val stagingDir = FsDir(workspace, draft.layoutId)
            val archiveTmp = FsFile(workspace, "$archiveName.tmp")
            val archiveDst = archiveRef.absoluteFile(context)

            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            archiveTmp.delete()
            archiveDst.parentFile?.mkdirs()

            writeManifest(stagingDir, extension)
            writeArrangement(stagingDir, draft)
            ZipUtils.zip(stagingDir, archiveTmp)
            archiveTmp.copyTo(archiveDst, overwrite = true)

            stagingDir.deleteRecursively()
            archiveTmp.delete()
            extensionManager.keyboardExtensions.init()
            CustomLayoutEditorPolicy.componentNameFor(draft.layoutId)
        }
    }

    private fun writeManifest(stagingDir: FsDir, extension: KeyboardExtension) {
        val manifest: Extension = extension
        FsFile(stagingDir, ExtensionDefaults.MANIFEST_FILE_NAME).writeJson(manifest, ExtensionJsonConfig)
    }

    private fun writeArrangement(stagingDir: FsDir, draft: CustomLayoutEditorDraft) {
        val layoutFile = File(stagingDir, CustomLayoutEditorPolicy.arrangementPath(draft.layoutId))
        layoutFile.parentFile?.mkdirs()
        layoutFile.writeText(CustomLayoutEditorPolicy.encodeArrangement(draft))
    }
}
