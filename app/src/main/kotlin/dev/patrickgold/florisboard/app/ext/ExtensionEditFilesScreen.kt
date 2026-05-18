/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.query
import org.florisboard.lib.android.readToFile
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.mimeTypeFilterOf

const val FONTS = "fonts"
const val IMAGES = "images"

private const val MaxEditorAssetImportBytes = 25L * 1024L * 1024L

val MIME_TYPES = mapOf(
    FONTS to mimeTypeFilterOf(
        // Source: https://www.alienfactory.co.uk/articles/mime-types-for-web-fonts-in-bedsheet#mimeTypes
        "font/*",
        "application/font-*",
        "application/x-font-*",
        "application/vnd.ms-fontobject",
    ),
    IMAGES to mimeTypeFilterOf(
        "image/*",
    ),
)

internal object ExtensionEditorFileNames {
    private val UnsafeFileNameChars = Regex("""[\p{Cntrl}/\\:*?"<>|]+""")

    fun sanitizeFileName(displayName: String?, fallbackName: String): String {
        val sanitized = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(UnsafeFileNameChars, "_")
            ?.trim()
            ?.trim('.')
            ?.take(160)
            ?.takeIf { it.isNotBlank() }
        return sanitized ?: fallbackName
    }

    fun safeFileIn(dir: File, inputName: String): File? {
        val name = inputName.trim()
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.contains('/') || name.contains('\\')) return null
        if (name.any { it.isISOControl() }) return null
        val canonicalDir = dir.canonicalFile
        val candidate = canonicalDir.subFile(name).canonicalFile
        return candidate.takeIf { file ->
            file.parentFile?.canonicalFile?.toPath() == canonicalDir.toPath()
        }
    }
}

@Composable
fun ExtensionEditFilesScreen(workspace: CacheManager.ExtEditorWorkspace<*>) = FlorisScreen {
    title = stringRes(R.string.ext__editor__files__title)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invalidFileNameNotice = stringRes(R.string.ext__editor__files__rename_invalid)
    val fileAlreadyExistsNotice = stringRes(R.string.ext__editor__files__rename_exists)
    var isFileActionInProgress by rememberSaveable { mutableStateOf(false) }
    var lastNotice by rememberSaveable { mutableStateOf<ExtensionEditorFileNotice?>(null) }
    var lastNoticeDetail by rememberSaveable { mutableStateOf<String?>(null) }

    fun startFileAction() {
        isFileActionInProgress = true
        lastNotice = null
        lastNoticeDetail = null
    }

    fun finishFileAction(notice: ExtensionEditorFileNotice, detail: String? = null) {
        isFileActionInProgress = false
        lastNotice = notice
        lastNoticeDetail = detail
    }

    fun handleBackPress() {
        if (ExtensionEditorFilesPolicy.canLeave(isFileActionInProgress)) {
            workspace.currentAction = null
        }
    }

    navigationIcon {
        FlorisIconButton(
            onClick = { handleBackPress() },
            icon = Icons.Default.Close,
            enabled = ExtensionEditorFilesPolicy.canLeave(isFileActionInProgress),
        )
    }

    content {
        var version by rememberSaveable { mutableIntStateOf(0) }
        val fontFiles = remember(version) {
            workspace.extDir.subDir(FONTS).listFiles { it.isFile }.orEmpty().asList()
        }
        val imageFiles = remember(version) {
            workspace.extDir.subDir(IMAGES).listFiles { it.isFile }.orEmpty().asList()
        }

        var currentImportDest by remember { mutableStateOf<String?>(null) }
        var currentImportResult by remember { mutableStateOf<Pair<File, String>?>(null) }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                val dest = currentImportDest
                if (uri == null || dest == null) {
                    currentImportDest = null
                    return@rememberLauncherForActivityResult
                }
                if (!ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress)) {
                    return@rememberLauncherForActivityResult
                }
                startFileAction()
                scope.launch {
                    val importResult = runCatching {
                        withContext(Dispatchers.IO) {
                            val mimeType = context.contentResolver.getType(uri)
                            val filter = MIME_TYPES[dest]!!
                            check(filter.matches(mimeType)) {
                                "Given file mime type was '$mimeType', expected one of ${filter.types}"
                            }
                            val displayName = context.contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                            ).use { cursor ->
                                if (cursor == null || !cursor.moveToFirst()) return@use null
                                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (name >= 0 && !cursor.isNull(name)) cursor.getString(name) else null
                            }
                            val fileName = ExtensionEditorFileNames.sanitizeFileName(
                                displayName ?: uri.lastPathSegment,
                                fallbackName = "asset-${UUID.randomUUID()}",
                            )
                            val tempFile = context.cacheDir.subFile("temp_${UUID.randomUUID()}")
                            context.contentResolver.readToFile(uri, tempFile, MaxEditorAssetImportBytes)
                            tempFile to fileName
                        }
                    }
                    isFileActionInProgress = false
                    importResult.onSuccess { result ->
                        currentImportResult = result
                        lastNotice = null
                        lastNoticeDetail = null
                    }.onFailure { error ->
                        currentImportDest = null
                        currentImportResult = null
                        lastNotice = ExtensionEditorFileNotice.ImportFailure
                        lastNoticeDetail = error.localizedMessage ?: error.message
                    }
                }
            },
        )

        BackHandler {
            handleBackPress()
        }

        when (ExtensionEditorFilesPolicy.resolveNotice(isFileActionInProgress, lastNotice)) {
            ExtensionEditorFileNotice.None -> Unit
            ExtensionEditorFileNotice.FileActionInProgress -> FlorisProgressCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__action_in_progress),
                secondaryText = stringRes(R.string.ext__editor__files__action_in_progress_summary),
            )
            ExtensionEditorFileNotice.ImportSuccess -> FlorisSuccessCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__import_success),
                secondaryText = stringRes(R.string.ext__editor__files__import_success_summary),
            )
            ExtensionEditorFileNotice.RenameSuccess -> FlorisSuccessCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__rename_success),
                secondaryText = stringRes(R.string.ext__editor__files__rename_success_summary),
            )
            ExtensionEditorFileNotice.DeleteSuccess -> FlorisSuccessCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__delete_success),
                secondaryText = stringRes(R.string.ext__editor__files__delete_success_summary),
            )
            ExtensionEditorFileNotice.ImportFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__import_failure),
                secondaryText = stringRes(
                    R.string.ext__editor__files__import_failure_summary,
                    "error_message" to (lastNoticeDetail ?: stringRes(R.string.ext__import__error_details_unavailable)),
                ),
            )
            ExtensionEditorFileNotice.RenameFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__rename_failure),
                secondaryText = stringRes(
                    R.string.ext__editor__files__rename_failure_summary,
                    "error_message" to (lastNoticeDetail ?: stringRes(R.string.ext__import__error_details_unavailable)),
                ),
            )
            ExtensionEditorFileNotice.DeleteFailure -> FlorisErrorCard(
                modifier = Modifier.defaultFlorisOutlinedBox(),
                text = stringRes(R.string.ext__editor__files__delete_failure),
                secondaryText = stringRes(
                    R.string.ext__editor__files__delete_failure_summary,
                    "error_message" to (lastNoticeDetail ?: stringRes(R.string.ext__import__error_details_unavailable)),
                ),
            )
        }

        @Composable
        fun FileList(title: String, icon: ImageVector, files: List<File>, onAdd: () -> Unit) {
            var dialogFile by remember { mutableStateOf<File?>(null) }
            val actionsEnabled = ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress)
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Spacer(modifier = Modifier.width(24.dp))
                },
                trailingContent = {
                    IconButton(
                        enabled = actionsEnabled,
                        onClick = {
                            if (actionsEnabled) {
                                onAdd()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, null)
                    }
                },
            )
            for (file in files) {
                Preference(
                    onClick = {
                        if (actionsEnabled) {
                            dialogFile = file
                        }
                    },
                    icon = icon,
                    title = file.name,
                )
            }

            dialogFile?.let { file ->
                var fileNameInput by rememberSaveable { mutableStateOf(file.name) }
                JetPrefAlertDialog(
                    title = stringRes(R.string.general__properties),
                    confirmLabel = stringRes(R.string.action__apply),
                    dismissLabel = stringRes(R.string.action__cancel),
                    neutralLabel = stringRes(R.string.action__delete),
                    allowOutsideDismissal = true,
                    onNeutral = {
                        if (!ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress)) {
                            return@JetPrefAlertDialog
                        }
                        dialogFile = null
                        startFileAction()
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                file.delete()
                            }
                            finishFileAction(ExtensionEditorFilesPolicy.deleteResult(deleted))
                            if (deleted) {
                                version++
                            }
                        }
                    },
                    onConfirm = {
                        if (!ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress)) {
                            return@JetPrefAlertDialog
                        }
                        val parent = file.parentFile
                        val newFile = if (parent != null) {
                            ExtensionEditorFileNames.safeFileIn(parent, fileNameInput)
                        } else {
                            null
                        }
                        if (newFile == null) {
                            lastNotice = ExtensionEditorFileNotice.RenameFailure
                            lastNoticeDetail = invalidFileNameNotice
                            return@JetPrefAlertDialog
                        }
                        if (newFile.exists()) {
                            lastNotice = ExtensionEditorFileNotice.RenameFailure
                            lastNoticeDetail = fileAlreadyExistsNotice
                            return@JetPrefAlertDialog
                        }
                        dialogFile = null
                        startFileAction()
                        scope.launch {
                            val renamed = withContext(Dispatchers.IO) {
                                file.renameTo(newFile)
                            }
                            finishFileAction(ExtensionEditorFilesPolicy.renameResult(renamed))
                            if (renamed) {
                                version++
                            }
                        }
                    },
                    onDismiss = {
                        dialogFile = null
                    },
                ) {
                    JetPrefTextField(
                        labelText = stringRes(R.string.general__file_name),
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        singleLine = true,
                    )
                }
            }
        }

        FileList(
            title = stringRes(R.string.ext__editor__files__type_fonts),
            icon = Icons.Default.TextFields,
            files = fontFiles,
        ) {
            currentImportDest = FONTS
            importLauncher.launch("*/*")
        }

        FileList(
            title = stringRes(R.string.ext__editor__files__type_images),
            icon = Icons.Default.Photo,
            files = imageFiles,
        ) {
            currentImportDest = IMAGES
            importLauncher.launch("*/*")
        }

        val dest = currentImportDest
        val result = currentImportResult
        if (dest != null && result != null) {
            var fileNameInput by rememberSaveable { mutableStateOf(result.second) }
            JetPrefAlertDialog(
                title = stringRes(R.string.action__import_file),
                confirmLabel = stringRes(R.string.action__add),
                onConfirm = {
                    if (!ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress)) {
                        return@JetPrefAlertDialog
                    }
                    val fileName = fileNameInput.trim()
                    val dir = workspace.extDir.subDir(dest)
                    dir.mkdirs()
                    val file = ExtensionEditorFileNames.safeFileIn(dir, fileName)
                    if (file == null) {
                        lastNotice = ExtensionEditorFileNotice.ImportFailure
                        lastNoticeDetail = invalidFileNameNotice
                    } else if (file.exists()) {
                        lastNotice = ExtensionEditorFileNotice.ImportFailure
                        lastNoticeDetail = fileAlreadyExistsNotice
                    } else {
                        val tempFile = result.first
                        currentImportDest = null
                        currentImportResult = null
                        startFileAction()
                        scope.launch {
                            val imported = withContext(Dispatchers.IO) {
                                tempFile.renameTo(file).also { success ->
                                    if (!success) {
                                        tempFile.delete()
                                    }
                                }
                            }
                            finishFileAction(ExtensionEditorFilesPolicy.importResult(imported))
                            if (imported) {
                                version++
                            }
                        }
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = {
                    val tempFile = result.first
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            tempFile.delete()
                        }
                    }
                    currentImportDest = null
                    currentImportResult = null
                },
            ) {
                JetPrefTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    singleLine = true,
                )
            }
        }
    }
}
