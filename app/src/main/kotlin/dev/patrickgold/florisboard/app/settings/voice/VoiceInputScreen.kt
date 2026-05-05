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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.patrickgold.florisboard.app.settings.theme.DialogProperty
import dev.patrickgold.florisboard.ime.voice.VoiceCommandAction
import dev.patrickgold.florisboard.ime.voice.VoiceCommandCustomCommand
import dev.patrickgold.florisboard.ime.voice.VoiceCommandCustomCommands
import dev.patrickgold.florisboard.ime.voice.VoiceInputManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
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
    val customCommands by prefs.voice.customCommands.collectAsState()
    var commandDialogState by remember { mutableStateOf<VoiceCommandDialogState?>(null) }

    val openFutoFailedText = stringRes(R.string.settings__voice_input__open_futo_failed)
    val openFutoPermissionsFailedText =
        stringRes(R.string.settings__voice_input__open_futo_permissions_failed)

    fun refreshStatus() {
        status = voiceInputManager.readStatus()
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
                status.isFutoInstalled -> ::openFuto
                else -> {
                    { context.launchUrl(VoiceInputManager.FUTO_FDROID_URL) }
                }
            },
        )

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
                    VoiceCommandAction.entries.forEach { candidate ->
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
            onClick = onClick,
        )
        status.isFutoEnabled -> FlorisInfoCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_ready),
            secondaryText = stringRes(R.string.settings__voice_input__status_ready_summary),
            onClick = onClick,
        )
        status.isFutoInstalled -> FlorisWarningCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_futo_not_enabled),
            secondaryText = stringRes(R.string.settings__voice_input__status_futo_not_enabled_summary),
            onClick = onClick,
        )
        status.isAnyVoiceProviderEnabled -> FlorisWarningCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_other_provider),
            secondaryText = stringRes(R.string.settings__voice_input__status_other_provider_summary),
            onClick = onClick,
        )
        else -> FlorisErrorCard(
            modifier = modifier,
            text = stringRes(R.string.settings__voice_input__status_futo_not_installed),
            secondaryText = stringRes(R.string.settings__voice_input__status_futo_not_installed_summary),
            onClick = onClick,
        )
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
    }
}
