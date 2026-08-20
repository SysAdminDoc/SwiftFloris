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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisUnsavedChangesDialog
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.material.ui.JetPrefDropdown
import dev.patrickgold.jetpref.material.ui.JetPrefDropdownMenuDefaults
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisTouchTarget
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.curlyFormat

private data class SelectedLayoutKey(
    val rowIndex: Int = 0,
    val keyIndex: Int = 0,
)

@Composable
fun CustomLayoutEditorScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard__custom_layout_editor__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()
    val keyboardManager by context.keyboardManager()
    val scope = rememberCoroutineScope()
    val repository = remember(context, extensionManager) {
        CustomLayoutEditorRepository(context, extensionManager)
    }

    val layoutExtensions by keyboardManager.resources.layouts.collectAsState()
    val characterLayouts = layoutExtensions[LayoutType.CHARACTERS] ?: emptyMap()
    val sourceLayoutNames = remember(characterLayouts) {
        characterLayouts.keys.sortedWith(compareBy({ it.extensionId }, { it.componentId }))
    }
    val existingComponentIds = remember(characterLayouts) {
        characterLayouts.keys.mapTo(mutableSetOf()) { it.componentId }
    }

    var selectedSource by remember { mutableStateOf<ExtensionComponentName?>(null) }
    var draft by remember { mutableStateOf<CustomLayoutEditorDraft?>(null) }
    var baselineDraft by remember { mutableStateOf<CustomLayoutEditorDraft?>(null) }
    var selectedKey by remember { mutableStateOf(SelectedLayoutKey()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pendingSourceSelection by remember { mutableStateOf<ExtensionComponentName?>(null) }
    var pendingBackNavigation by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sourceLayoutNames) {
        if (selectedSource == null && sourceLayoutNames.isNotEmpty()) {
            selectedSource = sourceLayoutNames.firstOrNull { it.componentId == "qwerty" } ?: sourceLayoutNames.first()
        }
    }

    LaunchedEffect(selectedSource) {
        val sourceName = selectedSource ?: return@LaunchedEffect
        val sourceComponent = characterLayouts[sourceName] ?: return@LaunchedEffect
        isLoading = true
        loadError = null
        statusMessage = null
        val loaded = repository.loadArrangement(sourceName, sourceComponent)
            .mapCatching { arrangement ->
                CustomLayoutEditorPolicy.newDraftFromArrangement(
                    context = context,
                    source = sourceComponent,
                    arrangement = arrangement,
                    existingComponentIds = existingComponentIds,
                ).getOrThrow()
            }
        loaded.onSuccess { newDraft ->
            draft = newDraft
            baselineDraft = newDraft
            selectedKey = SelectedLayoutKey()
            showValidation = false
        }.onFailure { error ->
            draft = null
            baselineDraft = null
            loadError = error.localizedMessage ?: error.message
        }
        isLoading = false
    }

    val activeDraft = draft
    val validation = activeDraft?.let {
        CustomLayoutEditorPolicy.validate(it, existingComponentIds)
    }
    val savedMessageTemplate = stringRes(R.string.settings__keyboard__custom_layout_editor__saved)
    val fallbackErrorTitle = stringRes(R.string.error__title)
    val hasUnsavedChanges = activeDraft != null && activeDraft != baselineDraft

    fun clearPendingNavigation() {
        pendingSourceSelection = null
        pendingBackNavigation = false
    }

    fun completePendingNavigation() {
        val nextSource = pendingSourceSelection
        val shouldPopBack = pendingBackNavigation
        clearPendingNavigation()
        if (nextSource != null) {
            selectedSource = nextSource
        } else if (shouldPopBack) {
            navController.popBackStack()
        }
    }

    fun requestLeaveEditor() {
        if (hasUnsavedChanges && !isSaving) {
            pendingBackNavigation = true
            pendingSourceSelection = null
            showUnsavedChangesDialog = true
        } else {
            navController.popBackStack()
        }
    }

    fun requestSourceSelection(source: ExtensionComponentName) {
        if (source == selectedSource || isLoading) return
        if (hasUnsavedChanges && !isSaving) {
            pendingSourceSelection = source
            pendingBackNavigation = false
            showUnsavedChangesDialog = true
        } else {
            selectedSource = source
        }
    }

    fun saveDraft(navigateAfterSave: Boolean = false) {
        val draftToSave = draft ?: return
        val currentValidation = CustomLayoutEditorPolicy.validate(draftToSave, existingComponentIds)
        if (!currentValidation.isValid) {
            showValidation = true
            statusMessage = null
            showUnsavedChangesDialog = false
            return
        }
        isSaving = true
        showValidation = false
        statusMessage = null
        scope.launch {
            repository.saveLocalLayout(draftToSave, existingComponentIds)
                .onSuccess { componentName ->
                    baselineDraft = draftToSave
                    statusMessage = savedMessageTemplate.curlyFormat(
                        "component_id" to componentName.componentId,
                    )
                    if (navigateAfterSave) {
                        completePendingNavigation()
                    }
                }
                .onFailure { error ->
                    statusMessage = error.localizedMessage ?: error.message
                        ?: fallbackErrorTitle
                }
            isSaving = false
        }
    }

    navigationIcon {
        FlorisIconButton(
            onClick = { requestLeaveEditor() },
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringRes(R.string.action__back),
            enabled = !isSaving,
        )
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                text = stringRes(R.string.action__cancel),
                enabled = !isSaving,
                onClick = { requestLeaveEditor() },
            )
            ButtonBarButton(
                text = if (isSaving) {
                    stringRes(R.string.settings__keyboard__custom_layout_editor__saving)
                } else {
                    stringRes(R.string.action__save)
                },
                enabled = activeDraft != null && !isLoading && !isSaving,
            ) {
                saveDraft()
            }
        }
    }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SourceLayoutSelector(
                sourceLayoutNames = sourceLayoutNames,
                characterLayouts = characterLayouts,
                selectedSource = selectedSource,
                isLoading = isLoading,
                onSelectedSourceChanged = { requestSourceSelection(it) },
            )

            loadError?.let { error ->
                StatusCard(text = stringRes(R.string.settings__keyboard__custom_layout_editor__load_error, "error" to error))
            }

            activeDraft?.let { currentDraft ->
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = currentDraft.label,
                    onValueChange = { label ->
                        draft = CustomLayoutEditorPolicy.updateLabel(currentDraft, label, existingComponentIds)
                    },
                    singleLine = true,
                    label = {
                        Text(text = stringRes(R.string.settings__keyboard__custom_layout_editor__name))
                    },
                    supportingText = {
                        Text(text = stringRes(
                            R.string.settings__keyboard__custom_layout_editor__id,
                            "id" to currentDraft.layoutId,
                        ))
                    },
                    isError = showValidation &&
                        validation?.errors?.contains(CustomLayoutEditorValidationError.BlankLabel) == true,
                )

                LayoutPreview(
                    draft = currentDraft,
                    selectedKey = selectedKey,
                    onSelectedKeyChanged = { selectedKey = it },
                )

                KeyEditor(
                    draft = currentDraft,
                    selectedKey = selectedKey.coerceInto(currentDraft),
                    onDraftChanged = { nextDraft ->
                        draft = nextDraft
                        selectedKey = selectedKey.coerceInto(nextDraft)
                    },
                    onSelectedKeyChanged = { selectedKey = it.coerceInto(currentDraft) },
                )

                if (showValidation && validation != null && !validation.isValid) {
                    ValidationCard(validation)
                }
            }

            statusMessage?.let { message ->
                StatusCard(text = message)
            }
        }
    }

    if (showUnsavedChangesDialog) {
        FlorisUnsavedChangesDialog(
            onSave = {
                showUnsavedChangesDialog = false
                saveDraft(navigateAfterSave = true)
            },
            onDiscard = {
                showUnsavedChangesDialog = false
                completePendingNavigation()
            },
            onDismiss = {
                showUnsavedChangesDialog = false
                clearPendingNavigation()
            },
        )
    }
}

@Composable
internal fun CustomLayoutEditorPreviewSurface() {
    val sourceName = remember {
        ExtensionComponentName("org.florisboard.layouts", "qwerty")
    }
    val sourceComponent = remember {
        LayoutArrangementComponent(
            id = "qwerty",
            label = "QWERTY",
            authors = listOf("SwiftFloris Contributors"),
            direction = "ltr",
        )
    }
    val existingComponentIds = remember { setOf(sourceName.componentId) }
    var selectedKey by remember { mutableStateOf(SelectedLayoutKey(rowIndex = 1, keyIndex = 4)) }
    var draft by remember {
        mutableStateOf(
            CustomLayoutEditorDraft(
                layoutId = "qwerty_number_row",
                label = "QWERTY Number Row",
                sourceLabel = sourceComponent.label,
                rows = listOf(
                    "1234567890".map { CustomLayoutEditorKey(it.toString()) },
                    "qwertyuiop".map { CustomLayoutEditorKey(it.toString()) },
                    "asdfghjkl".map { CustomLayoutEditorKey(it.toString()) },
                    "zxcvbnm".map { CustomLayoutEditorKey(it.toString()) },
                ),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SourceLayoutSelector(
            sourceLayoutNames = listOf(sourceName),
            characterLayouts = mapOf(sourceName to sourceComponent),
            selectedSource = sourceName,
            isLoading = false,
            onSelectedSourceChanged = { },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.label,
            onValueChange = { label ->
                draft = CustomLayoutEditorPolicy.updateLabel(draft, label, existingComponentIds)
            },
            singleLine = true,
            label = {
                Text(text = stringRes(R.string.settings__keyboard__custom_layout_editor__name))
            },
            supportingText = {
                Text(text = stringRes(
                    R.string.settings__keyboard__custom_layout_editor__id,
                    "id" to draft.layoutId,
                ))
            },
        )
        LayoutPreview(
            draft = draft,
            selectedKey = selectedKey.coerceInto(draft),
            onSelectedKeyChanged = { selectedKey = it },
        )
        KeyEditor(
            draft = draft,
            selectedKey = selectedKey.coerceInto(draft),
            onDraftChanged = { nextDraft ->
                draft = nextDraft
                selectedKey = selectedKey.coerceInto(nextDraft)
            },
            onSelectedKeyChanged = { selectedKey = it.coerceInto(draft) },
        )
    }
}

@Composable
private fun SourceLayoutSelector(
    sourceLayoutNames: List<ExtensionComponentName>,
    characterLayouts: Map<ExtensionComponentName, LayoutArrangementComponent>,
    selectedSource: ExtensionComponentName?,
    isLoading: Boolean,
    onSelectedSourceChanged: (ExtensionComponentName) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(R.string.settings__keyboard__custom_layout_editor__source),
            style = MaterialTheme.typography.titleSmall,
        )
        if (sourceLayoutNames.isEmpty()) {
            Text(
                text = stringRes(R.string.settings__keyboard__custom_layout_editor__no_sources),
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }
        val selectedIndex = sourceLayoutNames.indexOf(selectedSource).coerceAtLeast(0)
        val labels = remember(sourceLayoutNames, characterLayouts) {
            sourceLayoutNames.map { name ->
                characterLayouts[name]?.label ?: name.componentId
            }
        }
        val expanded = remember { mutableStateOf(false) }
        JetPrefDropdown(
            options = labels,
            expanded = expanded,
            selectedOptionIndex = selectedIndex,
            onSelectOption = { index ->
                if (!isLoading) {
                    onSelectedSourceChanged(sourceLayoutNames[index])
                }
            },
            appearance = JetPrefDropdownMenuDefaults.outlined(shape = ShapeDefaults.Small),
        )
    }
}

@Composable
private fun LayoutPreview(
    draft: CustomLayoutEditorDraft,
    selectedKey: SelectedLayoutKey,
    onSelectedKeyChanged: (SelectedLayoutKey) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(R.string.settings__keyboard__custom_layout_editor__preview),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            for ((rowIndex, row) in draft.rows.withIndex()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for ((keyIndex, key) in row.withIndex()) {
                        val isSelected = selectedKey.rowIndex == rowIndex && selectedKey.keyIndex == keyIndex
                        OutlinedButton(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .defaultMinSize(
                                    minWidth = FlorisTouchTarget.MinSize,
                                    minHeight = FlorisTouchTarget.MinSize,
                                ),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            onClick = { onSelectedKeyChanged(SelectedLayoutKey(rowIndex, keyIndex)) },
                        ) {
                            Text(
                                text = key.label.ifBlank { " " },
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyEditor(
    draft: CustomLayoutEditorDraft,
    selectedKey: SelectedLayoutKey,
    onDraftChanged: (CustomLayoutEditorDraft) -> Unit,
    onSelectedKeyChanged: (SelectedLayoutKey) -> Unit,
) {
    val row = draft.rows.getOrNull(selectedKey.rowIndex).orEmpty()
    val key = row.getOrNull(selectedKey.keyIndex)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringRes(
                R.string.settings__keyboard__custom_layout_editor__selected_key,
                "row" to selectedKey.rowIndex + 1,
                "key" to selectedKey.keyIndex + 1,
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = key?.label.orEmpty(),
            onValueChange = { label ->
                onDraftChanged(CustomLayoutEditorPolicy.updateKey(draft, selectedKey.rowIndex, selectedKey.keyIndex, label))
            },
            singleLine = true,
            label = { Text(text = stringRes(R.string.settings__keyboard__custom_layout_editor__key_label)) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = selectedKey.keyIndex > 0,
                onClick = {
                    onDraftChanged(CustomLayoutEditorPolicy.moveKey(draft, selectedKey.rowIndex, selectedKey.keyIndex, -1))
                    onSelectedKeyChanged(selectedKey.copy(keyIndex = selectedKey.keyIndex - 1))
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringRes(R.string.settings__keyboard__custom_layout_editor__move_left),
                )
            }
            IconButton(
                enabled = selectedKey.keyIndex < row.lastIndex,
                onClick = {
                    onDraftChanged(CustomLayoutEditorPolicy.moveKey(draft, selectedKey.rowIndex, selectedKey.keyIndex, 1))
                    onSelectedKeyChanged(selectedKey.copy(keyIndex = selectedKey.keyIndex + 1))
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringRes(R.string.settings__keyboard__custom_layout_editor__move_right),
                )
            }
            OutlinedButton(
                enabled = row.size < CustomLayoutEditorPolicy.MaxKeysPerRow,
                shape = MaterialTheme.shapes.small,
                onClick = {
                    onDraftChanged(CustomLayoutEditorPolicy.addKeyAfter(draft, selectedKey.rowIndex, selectedKey.keyIndex))
                    onSelectedKeyChanged(selectedKey.copy(keyIndex = selectedKey.keyIndex + 1))
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringRes(R.string.settings__keyboard__custom_layout_editor__add_key))
            }
            OutlinedButton(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    onDraftChanged(CustomLayoutEditorPolicy.removeKey(draft, selectedKey.rowIndex, selectedKey.keyIndex))
                    onSelectedKeyChanged(selectedKey.copy(keyIndex = selectedKey.keyIndex.coerceAtMost((row.size - 2).coerceAtLeast(0))))
                },
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringRes(R.string.action__delete))
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = draft.rows.size < CustomLayoutEditorPolicy.MaxRows,
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val nextDraft = CustomLayoutEditorPolicy.addRow(draft)
                    onDraftChanged(nextDraft)
                    onSelectedKeyChanged(SelectedLayoutKey(nextDraft.rows.lastIndex, 0))
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringRes(R.string.settings__keyboard__custom_layout_editor__add_row))
            }
            OutlinedButton(
                enabled = draft.rows.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val nextDraft = CustomLayoutEditorPolicy.removeRow(draft, selectedKey.rowIndex)
                    onDraftChanged(nextDraft)
                    onSelectedKeyChanged(selectedKey.coerceInto(nextDraft))
                },
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringRes(R.string.settings__keyboard__custom_layout_editor__delete_row))
            }
        }
    }
}

@Composable
private fun ValidationCard(validation: CustomLayoutEditorValidation) {
    val messages = validation.errors.map { error -> stringRes(error.messageRes()) }
    StatusCard(
        text = messages.joinToString(separator = "\n"),
        isError = true,
    )
}

@Composable
private fun StatusCard(
    text: String,
    isError: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun SelectedLayoutKey.coerceInto(draft: CustomLayoutEditorDraft): SelectedLayoutKey {
    if (draft.rows.isEmpty()) {
        return SelectedLayoutKey()
    }
    val rowIndex = rowIndex.coerceIn(0, draft.rows.lastIndex)
    val keyIndex = keyIndex.coerceIn(0, (draft.rows[rowIndex].size - 1).coerceAtLeast(0))
    return SelectedLayoutKey(rowIndex, keyIndex)
}

private fun CustomLayoutEditorValidationError.messageRes(): Int {
    return when (this) {
        CustomLayoutEditorValidationError.BlankLabel ->
            R.string.settings__keyboard__custom_layout_editor__error_blank_label
        CustomLayoutEditorValidationError.InvalidLayoutId ->
            R.string.settings__keyboard__custom_layout_editor__error_invalid_id
        CustomLayoutEditorValidationError.DuplicateLayoutId ->
            R.string.settings__keyboard__custom_layout_editor__error_duplicate_id
        CustomLayoutEditorValidationError.EmptyLayout ->
            R.string.settings__keyboard__custom_layout_editor__error_empty_layout
        CustomLayoutEditorValidationError.TooManyRows ->
            R.string.settings__keyboard__custom_layout_editor__error_too_many_rows
        CustomLayoutEditorValidationError.EmptyRow ->
            R.string.settings__keyboard__custom_layout_editor__error_empty_row
        CustomLayoutEditorValidationError.TooManyKeys ->
            R.string.settings__keyboard__custom_layout_editor__error_too_many_keys
        CustomLayoutEditorValidationError.BlankKey ->
            R.string.settings__keyboard__custom_layout_editor__error_blank_key
        CustomLayoutEditorValidationError.MultiCodePointKey ->
            R.string.settings__keyboard__custom_layout_editor__error_multi_key
        CustomLayoutEditorValidationError.ControlKey ->
            R.string.settings__keyboard__custom_layout_editor__error_control_key
    }
}
