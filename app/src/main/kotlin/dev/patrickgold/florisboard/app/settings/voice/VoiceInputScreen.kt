/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.voice

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.settings.theme.DialogProperty
import dev.patrickgold.florisboard.ime.voice.VoiceCommandAction
import dev.patrickgold.florisboard.ime.voice.VoiceCommandCustomCommand
import dev.patrickgold.florisboard.ime.voice.VoiceCommandCustomCommands
import dev.patrickgold.florisboard.ime.voice.VoiceDeviceRamProfile
import dev.patrickgold.florisboard.ime.voice.VoiceInputManager
import dev.patrickgold.florisboard.ime.voice.VoiceLocalRecognizerRuntime
import dev.patrickgold.florisboard.ime.voice.VoiceModelCatalog
import dev.patrickgold.florisboard.ime.voice.VoiceModelCatalogEntry
import dev.patrickgold.florisboard.ime.voice.VoiceModelEngine
import dev.patrickgold.florisboard.ime.voice.VoiceModelInstallRepository
import dev.patrickgold.florisboard.ime.voice.VoiceModelInstallState
import dev.patrickgold.florisboard.ime.voice.VoiceModelPreference
import dev.patrickgold.florisboard.ime.voice.VoiceModelSelector
import dev.patrickgold.florisboard.ime.voice.VoiceModelTier
import dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEnginePreference
import dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEngineRoute
import dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEngineRouteReason
import dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEngineSelection
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import java.util.Locale
import java.util.UUID

private data class VoiceInputStatus(
    val isFutoInstalled: Boolean,
    val isFutoEnabled: Boolean,
    val isFutoMicrophonePermissionGranted: Boolean,
    val isAnyVoiceProviderEnabled: Boolean,
)

private data class VoiceCommandDialogState(
    val command: VoiceCommandCustomCommand,
    val isNew: Boolean,
)

private val SupportedFutoVoiceLanguages = listOf(
    R.string.settings__voice_input__language_english,
    R.string.settings__voice_input__language_chinese,
    R.string.settings__voice_input__language_german,
    R.string.settings__voice_input__language_spanish,
    R.string.settings__voice_input__language_russian,
    R.string.settings__voice_input__language_french,
    R.string.settings__voice_input__language_portuguese,
    R.string.settings__voice_input__language_korean,
    R.string.settings__voice_input__language_japanese,
    R.string.settings__voice_input__language_turkish,
    R.string.settings__voice_input__language_polish,
    R.string.settings__voice_input__language_italian,
    R.string.settings__voice_input__language_swedish,
    R.string.settings__voice_input__language_dutch,
    R.string.settings__voice_input__language_catalan,
    R.string.settings__voice_input__language_finnish,
    R.string.settings__voice_input__language_indonesian,
)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun VoiceInputScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voice_input__title)
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext
    val voiceInputManager = remember(appContext) { VoiceInputManager(appContext) }
    var status by remember { mutableStateOf(voiceInputManager.readStatus()) }
    val ramProfile = remember(appContext) { VoiceModelSelector.detectDeviceRamProfile(appContext) }
    val localRecognizerRuntimeAvailable = VoiceLocalRecognizerRuntime.AVAILABLE
    val modelRepository = remember(appContext) { VoiceModelInstallRepository(appContext) }
    var modelStates by remember { mutableStateOf<Map<String, VoiceModelInstallState>>(emptyMap()) }
    var pendingModelImportId by rememberSaveable { mutableStateOf<String?>(null) }
    val recognitionEnginePreference by prefs.voice.recognitionEnginePreference.collectAsState()
    val embeddedModelPreference by prefs.voice.embeddedModelPreference.collectAsState()
    val resolvedEmbeddedModel = embeddedModelPreference.resolve(ramProfile)
    val hasEmbeddedWhisperModel = remember(modelStates, resolvedEmbeddedModel) {
        modelStates[VoiceModelCatalog.embeddedWhisperModelFor(resolvedEmbeddedModel).id]?.installed == true
    }
    val hasVoskStreamingModel = remember(modelStates) {
        VoiceModelCatalog.entries.any { model ->
            model.engine == VoiceModelEngine.VOSK_STREAMING && modelStates[model.id]?.installed == true
        }
    }
    val engineSelection = remember(
        status,
        ramProfile,
        recognitionEnginePreference,
        embeddedModelPreference,
        hasEmbeddedWhisperModel,
        hasVoskStreamingModel,
        localRecognizerRuntimeAvailable,
    ) {
        voiceInputManager.resolveRecognitionEngineSelection(
            enginePreference = recognitionEnginePreference,
            modelPreference = embeddedModelPreference,
            ramProfile = ramProfile,
            commandModeRequested = false,
            hasEmbeddedWhisperModel = hasEmbeddedWhisperModel,
            hasVoskStreamingModel = hasVoskStreamingModel,
            localRecognizerRuntimeAvailable = localRecognizerRuntimeAvailable,
        )
    }
    val commandModeEngineSelection = remember(
        status,
        ramProfile,
        recognitionEnginePreference,
        embeddedModelPreference,
        hasEmbeddedWhisperModel,
        hasVoskStreamingModel,
        localRecognizerRuntimeAvailable,
    ) {
        voiceInputManager.resolveRecognitionEngineSelection(
            enginePreference = recognitionEnginePreference,
            modelPreference = embeddedModelPreference,
            ramProfile = ramProfile,
            commandModeRequested = true,
            hasEmbeddedWhisperModel = hasEmbeddedWhisperModel,
            hasVoskStreamingModel = hasVoskStreamingModel,
            localRecognizerRuntimeAvailable = localRecognizerRuntimeAvailable,
        )
    }
    val customCommands by prefs.voice.customCommands.collectAsState()
    var commandDialogState by remember { mutableStateOf<VoiceCommandDialogState?>(null) }

    val openFutoFailedText = stringRes(R.string.settings__voice_input__open_futo_failed)
    val openFutoPermissionsFailedText =
        stringRes(R.string.settings__voice_input__open_futo_permissions_failed)
    val modelImportedText = stringRes(R.string.settings__voice_input__model_imported)
    val modelImportFailedText = stringRes(R.string.settings__voice_input__model_import_failed)
    val modelRemovedText = stringRes(R.string.settings__voice_input__model_removed)

    val modelImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val model = VoiceModelCatalog.byId(pendingModelImportId)
        pendingModelImportId = null
        if (uri != null && model != null) {
            scope.launch {
                runCatching {
                    modelRepository.installFromUri(model, uri)
                }.onSuccess {
                    modelStates = modelRepository.states(VoiceModelCatalog.entries)
                    Toast.makeText(context, modelImportedText, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, modelImportFailedText, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(modelRepository) {
        modelStates = modelRepository.states(VoiceModelCatalog.entries)
    }

    fun refreshStatus() {
        status = voiceInputManager.readStatus()
    }

    fun importVoiceModel(model: VoiceModelCatalogEntry) {
        pendingModelImportId = model.id
        modelImportLauncher.launch(
            arrayOf(
                "application/octet-stream",
                "application/zip",
                "application/x-zip-compressed",
                "*/*",
            ),
        )
    }

    fun deleteVoiceModel(model: VoiceModelCatalogEntry) {
        scope.launch {
            modelRepository.delete(model)
            modelStates = modelRepository.states(VoiceModelCatalog.entries)
            Toast.makeText(context, modelRemovedText, Toast.LENGTH_SHORT).show()
        }
    }

    fun openFuto() {
        if (!voiceInputManager.launchFutoVoiceInputApp()) {
            Toast.makeText(context, openFutoFailedText, Toast.LENGTH_LONG).show()
        }
    }

    fun openFutoAppSettings() {
        if (!voiceInputManager.launchFutoAppInfoSettings()) {
            Toast.makeText(context, openFutoPermissionsFailedText, Toast.LENGTH_LONG).show()
        }
    }

    fun openKeyboardSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.voice_input_setup__open_failed, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.voice_input_setup__open_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun updateCustomCommands(commands: VoiceCommandCustomCommands) {
        scope.launch {
            prefs.voice.customCommands.set(commands)
        }
    }

    DisposableEffect(lifecycleOwner, voiceInputManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    content {
        VoiceInputStatusCard(
            modifier = Modifier.padding(8.dp),
            status = status,
            onClick = when {
                status.isFutoEnabled && !status.isFutoMicrophonePermissionGranted -> ::openFutoAppSettings
                status.isFutoInstalled && !status.isFutoEnabled -> ::openKeyboardSettings
                status.isFutoInstalled -> ::openFuto
                else -> {
                    { context.launchUrl(VoiceInputManager.FUTO_FDROID_URL) }
                }
            },
        )

        PreferenceGroup(title = stringRes(R.string.settings__voice_input__group_embedded_engine)) {
            val recommendedModel = VoiceModelSelector.recommend(ramProfile)
            val resolvedModel = embeddedModelPreference.resolve(ramProfile)
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__voice_input__embedded_engine_info_title),
                secondaryText = stringRes(R.string.settings__voice_input__embedded_engine_info_summary),
                showIcon = false,
            )
            ListPreference(
                prefs.voice.recognitionEnginePreference,
                title = stringRes(R.string.settings__voice_input__recognition_engine_preference),
                entries = enumDisplayEntriesOf(VoiceRecognitionEnginePreference::class),
            )
            ListPreference(
                prefs.voice.embeddedModelPreference,
                title = stringRes(R.string.settings__voice_input__embedded_model_preference),
                entries = enumDisplayEntriesOf(VoiceModelPreference::class),
            )
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__voice_input__streaming_fallback_info_title),
                secondaryText = stringRes(R.string.settings__voice_input__streaming_fallback_info_summary),
                showIcon = false,
            )
            VoiceRecognitionEngineRouteRow(
                title = stringRes(R.string.settings__voice_input__recognition_engine_route),
                selection = engineSelection,
            )
            VoiceRecognitionEngineRouteRow(
                title = stringRes(R.string.settings__voice_input__command_mode_route),
                selection = commandModeEngineSelection,
            )
            JetPrefListItem(
                text = stringRes(R.string.settings__voice_input__embedded_model_selection),
                secondaryText = stringRes(
                    R.string.settings__voice_input__embedded_model_selection_summary,
                    "selected" to voiceModelTierLabel(resolvedModel),
                    "recommended" to voiceModelTierLabel(recommendedModel),
                    "ram" to voiceRamSummary(ramProfile),
                ),
            )
            JetPrefListItem(
                text = stringRes(R.string.settings__voice_input__embedded_model_recommendation),
                secondaryText = stringRes(
                    R.string.settings__voice_input__embedded_model_recommendation_summary,
                    "tier" to voiceModelTierLabel(recommendedModel),
                    "size" to recommendedModel.approximateSizeMb,
                    "ram" to voiceRamSummary(ramProfile),
                ),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice_input__group_local_models)) {
            if (localRecognizerRuntimeAvailable) {
                FlorisInfoCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.settings__voice_input__local_models_info_title),
                    secondaryText = stringRes(R.string.settings__voice_input__local_models_info_summary),
                    showIcon = false,
                )
            } else {
                FlorisWarningCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.settings__voice_input__local_models_info_title),
                    secondaryText = stringRes(R.string.settings__voice_input__local_models_info_summary),
                )
            }
            VoiceModelCatalog.entries
                .groupBy { it.languageName }
                .forEach { (languageName, models) ->
                    JetPrefListItem(
                        text = languageName,
                        secondaryText = stringRes(
                            R.string.settings__voice_input__local_model_language_summary,
                            "count" to models.size,
                        ),
                    )
                    models.forEach { model ->
                        VoiceModelRow(
                            model = model,
                            state = modelStates[model.id],
                            localRecognizerRuntimeAvailable = localRecognizerRuntimeAvailable,
                            onDownload = { context.launchUrl(model.sourceUrl) },
                            onImport = { importVoiceModel(model) },
                            onDelete = { deleteVoiceModel(model) },
                        )
                    }
                }
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice_input__group_setup)) {
            Preference(
                icon = Icons.Default.Mic,
                title = stringRes(R.string.settings__voice_input__open_futo_language_settings),
                summary = stringRes(R.string.settings__voice_input__open_futo_language_settings_summary),
                onClick = {
                    if (status.isFutoInstalled) {
                        openFuto()
                    } else {
                        context.launchUrl(VoiceInputManager.FUTO_FDROID_URL)
                    }
                },
            )
            Preference(
                icon = Icons.Default.Language,
                title = stringRes(R.string.voice_input_setup__open_keyboard_settings),
                summary = stringRes(R.string.settings__voice_input__keyboard_settings_summary),
                onClick = ::openKeyboardSettings,
            )
            if (status.isFutoInstalled) {
                Preference(
                    icon = Icons.Default.Mic,
                    title = stringRes(R.string.settings__voice_input__open_futo_permissions),
                    summary = stringRes(R.string.settings__voice_input__open_futo_permissions_summary),
                    onClick = ::openFutoAppSettings,
                )
            }
            if (!status.isFutoInstalled) {
                Preference(
                    icon = Icons.Default.Download,
                    title = stringRes(R.string.voice_input_setup__install_fdroid),
                    summary = stringRes(R.string.settings__voice_input__install_summary),
                    onClick = { context.launchUrl(VoiceInputManager.FUTO_FDROID_URL) },
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice_input__group_voice_commands)) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__voice_input__voice_commands_info_title),
                secondaryText = stringRes(R.string.settings__voice_input__voice_commands_info_summary),
                showIcon = false,
            )
            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.settings__voice_input__custom_command_add),
                summary = stringRes(R.string.settings__voice_input__custom_command_add_summary),
                onClick = {
                    commandDialogState = VoiceCommandDialogState(
                        command = VoiceCommandCustomCommand(
                            id = UUID.randomUUID().toString(),
                            phrase = "",
                            action = VoiceCommandAction.NEW_LINE,
                        ),
                        isNew = true,
                    )
                },
            )
            if (customCommands.commands.isEmpty()) {
                JetPrefListItem(
                    text = stringRes(R.string.settings__voice_input__custom_commands_empty_title),
                    secondaryText = stringRes(R.string.settings__voice_input__custom_commands_empty_summary),
                )
            }
            for (command in customCommands.commands) {
                val actionLabel = stringRes(command.action.labelRes())
                JetPrefListItem(
                    modifier = Modifier.rippleClickable {
                        commandDialogState = VoiceCommandDialogState(command = command, isNew = false)
                    },
                    text = if (command.phrase.isBlank()) {
                        stringRes(R.string.settings__voice_input__custom_command_blank_phrase)
                    } else {
                        command.phrase
                    },
                    secondaryText = if (command.enabled) {
                        actionLabel
                    } else {
                        stringRes(
                            R.string.settings__voice_input__custom_command_disabled_summary,
                            "action" to actionLabel,
                        )
                    },
                    trailing = {
                        Checkbox(
                            checked = command.enabled,
                            onCheckedChange = { enabled ->
                                updateCustomCommands(
                                    customCommands.upsert(command.copy(enabled = enabled)),
                                )
                            },
                        )
                    },
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__voice_input__group_language_packs)) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__voice_input__language_pack_handoff_title),
                secondaryText = stringRes(R.string.settings__voice_input__language_pack_handoff_summary),
                showIcon = false,
            )
            for (language in SupportedFutoVoiceLanguages) {
                JetPrefListItem(
                    text = stringRes(language),
                    secondaryText = stringRes(R.string.settings__voice_input__supported_language_summary),
                )
            }
        }

        commandDialogState?.let { dialogState ->
            VoiceCommandEditDialog(
                state = dialogState,
                existingCommands = customCommands,
                onSave = { command ->
                    updateCustomCommands(customCommands.upsert(command))
                    commandDialogState = null
                },
                onDelete = {
                    updateCustomCommands(customCommands.remove(dialogState.command.id))
                    commandDialogState = null
                },
                onDismiss = {
                    commandDialogState = null
                },
            )
        }
    }
}

@Composable
private fun VoiceCommandEditDialog(
    state: VoiceCommandDialogState,
    existingCommands: VoiceCommandCustomCommands,
    onSave: (VoiceCommandCustomCommand) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var phrase by rememberSaveable(state.command.id) { mutableStateOf(state.command.phrase) }
    var action by rememberSaveable(state.command.id) { mutableStateOf(state.command.action) }
    var enabled by rememberSaveable(state.command.id) { mutableStateOf(state.command.enabled) }
    var showValidationErrors by rememberSaveable(state.command.id) { mutableStateOf(false) }

    val normalizedPhrase = phrase.trim()
    val hasDuplicatePhrase = existingCommands.commands.any { command ->
        command.id != state.command.id && command.phrase.trim().equals(normalizedPhrase, ignoreCase = true)
    }
    val phraseError = when {
        normalizedPhrase.isEmpty() -> R.string.settings__voice_input__custom_command_phrase_required
        hasDuplicatePhrase -> R.string.settings__voice_input__custom_command_phrase_duplicate
        else -> null
    }

    JetPrefAlertDialog(
        title = stringRes(
            if (state.isNew) {
                R.string.settings__voice_input__custom_command_dialog_add
            } else {
                R.string.settings__voice_input__custom_command_dialog_edit
            },
        ),
        confirmLabel = stringRes(if (state.isNew) R.string.action__add else R.string.action__apply),
        onConfirm = {
            if (phraseError != null) {
                showValidationErrors = true
            } else {
                onSave(
                    state.command.copy(
                        phrase = normalizedPhrase,
                        action = action,
                        enabled = enabled,
                    ),
                )
            }
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        neutralLabel = if (state.isNew) null else stringRes(R.string.action__delete),
        onNeutral = onDelete,
    ) {
        Column {
            DialogProperty(text = stringRes(R.string.settings__voice_input__custom_command_phrase_label)) {
                JetPrefTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                )
                if (showValidationErrors && phraseError != null) {
                    Text(
                        text = stringRes(phraseError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            DialogProperty(text = stringRes(R.string.settings__voice_input__custom_command_enabled_label)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.rippleClickable { enabled = !enabled },
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                    Text(text = stringRes(R.string.settings__voice_input__custom_command_enabled_summary))
                }
            }
            DialogProperty(text = stringRes(R.string.settings__voice_input__custom_command_action_label)) {
                Column {
                    // REMOVE_ITEM_FROM_LIST is parameterised — it requires an
                    // argument extracted from the spoken utterance — and is
                    // not assignable as a fixed-phrase custom command.
                    VoiceCommandAction.entries
                        .filter { it != VoiceCommandAction.REMOVE_ITEM_FROM_LIST }
                        .forEach { candidate ->
                            JetPrefListItem(
                                modifier = Modifier.rippleClickable { action = candidate },
                                icon = {
                                    RadioButton(
                                        selected = action == candidate,
                                        onClick = null,
                                    )
                                },
                                text = stringRes(candidate.labelRes()),
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun VoiceModelRow(
    model: VoiceModelCatalogEntry,
    state: VoiceModelInstallState?,
    localRecognizerRuntimeAvailable: Boolean,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    val installedState = state?.takeIf { it.installed }
    val installed = installedState != null
    val installSummary = installedState?.let {
        stringRes(
            R.string.settings__voice_input__local_model_installed,
            "size" to voiceModelDiskUsage(it.diskBytes),
        )
    } ?: stringRes(R.string.settings__voice_input__local_model_not_installed)
    JetPrefListItem(
        text = model.displayName,
        secondaryText = listOf(
            voiceModelEngineLabel(model.engine),
            stringRes(
                R.string.settings__voice_input__local_model_size_summary,
                "size" to model.approximateSizeMb,
            ),
            model.license,
            installSummary,
        ).joinToString(" - "),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDownload,
                    enabled = localRecognizerRuntimeAvailable,
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringRes(R.string.settings__voice_input__local_model_download),
                    )
                }
                IconButton(
                    onClick = onImport,
                    enabled = localRecognizerRuntimeAvailable,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringRes(R.string.settings__voice_input__local_model_import),
                    )
                }
                if (installed) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringRes(R.string.settings__voice_input__local_model_delete),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun voiceModelEngineLabel(engine: VoiceModelEngine): String {
    return when (engine) {
        VoiceModelEngine.WHISPER_CPP -> stringRes(R.string.enum__voice_model_engine__whisper_cpp)
        VoiceModelEngine.VOSK_STREAMING -> stringRes(R.string.enum__voice_model_engine__vosk_streaming)
    }
}

private fun voiceModelDiskUsage(bytes: Long): String {
    val mib = bytes / (1_024.0 * 1_024.0)
    return when {
        mib >= 100.0 -> String.format(Locale.ROOT, "%.0f MB", mib)
        mib >= 10.0 -> String.format(Locale.ROOT, "%.1f MB", mib)
        mib > 0.0 -> String.format(Locale.ROOT, "%.2f MB", mib)
        else -> "0 MB"
    }
}

@Composable
private fun VoiceRecognitionEngineRouteRow(
    title: String,
    selection: VoiceRecognitionEngineSelection,
) {
    JetPrefListItem(
        text = title,
        secondaryText = stringRes(
            R.string.settings__voice_input__recognition_engine_route_summary,
            "route" to voiceRecognitionEngineRouteLabel(selection.route),
            "reason" to voiceRecognitionEngineRouteReasonLabel(selection.reason),
        ),
    )
}

@Composable
private fun VoiceInputStatusCard(
    modifier: Modifier,
    status: VoiceInputStatus,
    onClick: () -> Unit,
) {
    when {
        status.isFutoEnabled && !status.isFutoMicrophonePermissionGranted -> FlorisWarningCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_permission_denied),
            secondaryText = stringRes(R.string.settings__voice_input__status_permission_denied_summary),
            actionLabel = stringRes(R.string.voice_input_setup__open_futo_permissions),
            onClick = onClick,
        )
        status.isFutoEnabled -> FlorisInfoCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_ready),
            secondaryText = stringRes(R.string.settings__voice_input__status_ready_summary),
            actionLabel = stringRes(R.string.settings__voice_input__open_futo_language_settings),
            onClick = onClick,
        )
        status.isFutoInstalled -> FlorisWarningCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_futo_not_enabled),
            secondaryText = stringRes(R.string.settings__voice_input__status_futo_not_enabled_summary),
            actionLabel = stringRes(R.string.voice_input_setup__open_keyboard_settings),
            onClick = onClick,
        )
        status.isAnyVoiceProviderEnabled -> FlorisWarningCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_other_provider),
            secondaryText = stringRes(R.string.settings__voice_input__status_other_provider_summary),
            actionLabel = stringRes(R.string.voice_input_setup__open_keyboard_settings),
            onClick = onClick,
        )
        else -> FlorisErrorCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_futo_not_installed),
            secondaryText = stringRes(R.string.settings__voice_input__status_futo_not_installed_summary),
            actionLabel = stringRes(R.string.voice_input_setup__install_fdroid),
            onClick = onClick,
        )
    }
}

@Composable
private fun voiceRecognitionEngineRouteLabel(route: VoiceRecognitionEngineRoute): String {
    return when (route) {
        VoiceRecognitionEngineRoute.EMBEDDED_WHISPER ->
            stringRes(R.string.enum__voice_recognition_engine_route__embedded_whisper)
        VoiceRecognitionEngineRoute.VOSK_STREAMING ->
            stringRes(R.string.enum__voice_recognition_engine_route__vosk_streaming)
        VoiceRecognitionEngineRoute.EXTERNAL_IME ->
            stringRes(R.string.enum__voice_recognition_engine_route__external_ime)
        VoiceRecognitionEngineRoute.UNAVAILABLE ->
            stringRes(R.string.enum__voice_recognition_engine_route__unavailable)
    }
}

@Composable
private fun voiceRecognitionEngineRouteReasonLabel(reason: VoiceRecognitionEngineRouteReason): String {
    return when (reason) {
        VoiceRecognitionEngineRouteReason.AUTO_COMMAND_MODE_VOSK ->
            stringRes(R.string.enum__voice_recognition_engine_reason__auto_command_mode_vosk)
        VoiceRecognitionEngineRouteReason.AUTO_LOW_RAM_VOSK ->
            stringRes(R.string.enum__voice_recognition_engine_reason__auto_low_ram_vosk)
        VoiceRecognitionEngineRouteReason.AUTO_EMBEDDED_WHISPER ->
            stringRes(R.string.enum__voice_recognition_engine_reason__auto_embedded_whisper)
        VoiceRecognitionEngineRouteReason.AUTO_VOSK_WHISPER_MISSING ->
            stringRes(R.string.enum__voice_recognition_engine_reason__auto_vosk_whisper_missing)
        VoiceRecognitionEngineRouteReason.EXPLICIT_EMBEDDED_WHISPER ->
            stringRes(R.string.enum__voice_recognition_engine_reason__explicit_embedded_whisper)
        VoiceRecognitionEngineRouteReason.EXPLICIT_VOSK_STREAMING ->
            stringRes(R.string.enum__voice_recognition_engine_reason__explicit_vosk_streaming)
        VoiceRecognitionEngineRouteReason.EXPLICIT_EXTERNAL_IME ->
            stringRes(R.string.enum__voice_recognition_engine_reason__explicit_external_ime)
        VoiceRecognitionEngineRouteReason.FALLBACK_EXTERNAL_WHILE_LOCAL_UNAVAILABLE ->
            stringRes(R.string.enum__voice_recognition_engine_reason__fallback_external)
        VoiceRecognitionEngineRouteReason.LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE ->
            stringRes(R.string.enum__voice_recognition_engine_reason__local_runtime_unavailable)
        VoiceRecognitionEngineRouteReason.LOCAL_MIC_PERMISSION_MISSING ->
            stringRes(R.string.enum__voice_recognition_engine_reason__local_mic_permission_missing)
        VoiceRecognitionEngineRouteReason.EMBEDDED_WHISPER_MODEL_MISSING ->
            stringRes(R.string.enum__voice_recognition_engine_reason__embedded_model_missing)
        VoiceRecognitionEngineRouteReason.VOSK_MODEL_MISSING ->
            stringRes(R.string.enum__voice_recognition_engine_reason__vosk_model_missing)
        VoiceRecognitionEngineRouteReason.EXTERNAL_IME_NOT_READY ->
            stringRes(R.string.enum__voice_recognition_engine_reason__external_ime_not_ready)
        VoiceRecognitionEngineRouteReason.NO_VOICE_ENGINE_AVAILABLE ->
            stringRes(R.string.enum__voice_recognition_engine_reason__no_engine_available)
    }
}

private fun VoiceInputManager.readStatus(): VoiceInputStatus {
    return VoiceInputStatus(
        isFutoInstalled = isFutoVoiceInputInstalled(),
        isFutoEnabled = isFutoVoiceInputEnabled(),
        isFutoMicrophonePermissionGranted = isFutoMicrophonePermissionGranted(),
        isAnyVoiceProviderEnabled = isExternalVoiceInputMethodEnabled(),
    )
}

private fun VoiceCommandAction.labelRes(): Int {
    return when (this) {
        VoiceCommandAction.DELETE_THAT -> R.string.settings__voice_input__voice_command_delete_that
        VoiceCommandAction.UNDO -> R.string.settings__voice_input__voice_command_undo
        VoiceCommandAction.REDO -> R.string.settings__voice_input__voice_command_redo
        VoiceCommandAction.SELECT_ALL -> R.string.settings__voice_input__voice_command_select_all
        VoiceCommandAction.CLEAR_TEXT -> R.string.settings__voice_input__voice_command_clear_text
        VoiceCommandAction.NEW_PARAGRAPH -> R.string.settings__voice_input__voice_command_new_paragraph
        VoiceCommandAction.NEW_LINE -> R.string.settings__voice_input__voice_command_new_line
        VoiceCommandAction.CAPITALIZE_NEXT_WORD ->
            R.string.settings__voice_input__voice_command_capitalize_next_word
        VoiceCommandAction.GO_TO_START -> R.string.settings__voice_input__voice_command_go_to_start
        VoiceCommandAction.GO_TO_END -> R.string.settings__voice_input__voice_command_go_to_end
        VoiceCommandAction.REMOVE_ITEM_FROM_LIST ->
            R.string.settings__voice_input__voice_command_remove_item_from_list
    }
}

@Composable
private fun voiceModelTierLabel(tier: VoiceModelTier): String {
    return when (tier) {
        VoiceModelTier.TINY_EN -> stringRes(R.string.enum__voice_model_preference__tiny_en)
        VoiceModelTier.BASE_EN -> stringRes(R.string.enum__voice_model_preference__base_en)
        VoiceModelTier.LARGE_V3_TURBO_INT8 -> stringRes(
            R.string.enum__voice_model_preference__large_v3_turbo_int8,
        )
    }
}

@Composable
private fun voiceRamSummary(profile: VoiceDeviceRamProfile): String {
    val totalRamMb = profile.totalRamMb
        ?: return stringRes(R.string.settings__voice_input__embedded_model_ram_unknown)
    val totalRamGb = String.format(Locale.ROOT, "%.1f", totalRamMb / 1_024.0)
    return stringRes(
        R.string.settings__voice_input__embedded_model_ram_detected,
        "ram" to totalRamGb,
    )
}
