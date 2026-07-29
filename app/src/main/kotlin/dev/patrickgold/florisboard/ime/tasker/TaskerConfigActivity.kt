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

package dev.patrickgold.florisboard.ime.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes

class TaskerConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != TaskerIntentContract.Plugin.ACTION_EDIT_SETTING) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED)
        val initial = intent.readTaskerPluginJson()
            ?.let(TaskerIntentContract::decodeForEditing)
        setContent {
            Content(initial)
        }
    }

    @Composable
    private fun Content(initial: TaskerPluginAction?) {
        val prefs by FlorisPreferenceStore
        val theme by prefs.other.settingsTheme.collectAsState()
        val automationEnabled by prefs.privacy.externalAutomationEnabled.collectAsState()

        ProvideLocalizedResources(
            resourcesContext = this,
            appName = R.string.app_name,
        ) {
            FlorisAppTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TaskerConfigScreen(
                        initial = initial,
                        automationEnabled = automationEnabled,
                        onCancel = ::finish,
                        onSave = ::saveConfiguration,
                    )
                }
            }
        }
    }

    private fun saveConfiguration(action: String, extras: Map<String, Any?>): Boolean {
        val json = TaskerAuthentication.createAuthenticatedJson(
            context = this,
            action = action,
            extras = extras,
        ) ?: return false
        val pluginBundle = Bundle().apply {
            putString(TaskerIntentContract.Plugin.EXTRA_STRING_JSON, json)
        }
        val result = Intent()
            .putExtra(TaskerIntentContract.Plugin.EXTRA_BUNDLE, pluginBundle)
            .putExtra(
                TaskerIntentContract.Plugin.EXTRA_STRING_BLURB,
                TaskerIntentContract.blurb(action, extras),
            )
        setResult(Activity.RESULT_OK, result)
        finish()
        return true
    }
}

@Composable
internal fun TaskerConfigScreen(
    initial: TaskerPluginAction?,
    automationEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: (String, Map<String, Any?>) -> Boolean,
) {
    var selectedAction by rememberSaveable {
        mutableStateOf(initial?.action ?: TaskerIntentContract.InsertText.ACTION)
    }
    var text by rememberSaveable {
        mutableStateOf(initial?.extras?.get(TaskerIntentContract.InsertText.EXTRA_TEXT) as? String ?: "")
    }
    var appendSpace by rememberSaveable {
        mutableStateOf(
            initial?.extras?.get(TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE) as? Boolean
                ?: false,
        )
    }
    var layoutId by rememberSaveable {
        mutableStateOf(
            initial?.extras?.get(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID) as? String
                ?: "",
        )
    }
    var voiceMode by rememberSaveable {
        mutableStateOf(
            initial?.extras?.get(TaskerIntentContract.TriggerVoice.EXTRA_MODE) as? String
                ?: "dictation",
        )
    }
    var saveFailed by rememberSaveable { mutableStateOf(false) }

    val extras = when (selectedAction) {
        TaskerIntentContract.InsertText.ACTION -> buildMap<String, Any?> {
            put(TaskerIntentContract.InsertText.EXTRA_TEXT, text)
            if (appendSpace) {
                put(TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE, true)
            }
        }
        TaskerIntentContract.InsertClipboard.ACTION -> emptyMap()
        TaskerIntentContract.SwitchLayout.ACTION -> mapOf(
            TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to layoutId,
        )
        TaskerIntentContract.TriggerVoice.ACTION -> mapOf(
            TaskerIntentContract.TriggerVoice.EXTRA_MODE to voiceMode,
        )
        else -> emptyMap()
    }
    val isValid = TaskerIntentContract.validate(selectedAction, extras) == ValidationResult.Accept

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringRes(R.string.tasker_plugin__title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringRes(R.string.tasker_plugin__intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!automationEnabled) {
            Text(
                text = stringRes(R.string.tasker_plugin__disabled_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringRes(R.string.tasker_plugin__action_label),
            style = MaterialTheme.typography.titleMedium,
        )
        TaskerActionOption(
            selected = selectedAction == TaskerIntentContract.InsertText.ACTION,
            label = stringRes(R.string.tasker_plugin__insert_text),
            onClick = { selectedAction = TaskerIntentContract.InsertText.ACTION },
        )
        TaskerActionOption(
            selected = selectedAction == TaskerIntentContract.InsertClipboard.ACTION,
            label = stringRes(R.string.tasker_plugin__insert_clipboard),
            onClick = { selectedAction = TaskerIntentContract.InsertClipboard.ACTION },
        )
        TaskerActionOption(
            selected = selectedAction == TaskerIntentContract.SwitchLayout.ACTION,
            label = stringRes(R.string.tasker_plugin__switch_layout),
            onClick = { selectedAction = TaskerIntentContract.SwitchLayout.ACTION },
        )
        TaskerActionOption(
            selected = selectedAction == TaskerIntentContract.TriggerVoice.ACTION,
            label = stringRes(R.string.tasker_plugin__trigger_voice),
            onClick = { selectedAction = TaskerIntentContract.TriggerVoice.ACTION },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        when (selectedAction) {
            TaskerIntentContract.InsertText.ACTION -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = {
                        text = it.take(TaskerIntentContract.MAX_INSERT_LENGTH)
                        saveFailed = false
                    },
                    label = { Text(stringRes(R.string.tasker_plugin__text_label)) },
                    supportingText = {
                        Text(
                            stringRes(
                                R.string.tasker_plugin__text_count,
                                "count" to text.length,
                                "limit" to TaskerIntentContract.MAX_INSERT_LENGTH,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    minLines = 3,
                )
                ToggleRow(
                    checked = appendSpace,
                    label = stringRes(R.string.tasker_plugin__append_space),
                    onCheckedChange = {
                        appendSpace = it
                        saveFailed = false
                    },
                )
            }
            TaskerIntentContract.InsertClipboard.ACTION -> {
                Text(
                    text = stringRes(R.string.tasker_plugin__clipboard_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TaskerIntentContract.SwitchLayout.ACTION -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = layoutId,
                    onValueChange = {
                        layoutId = it.take(32)
                        saveFailed = false
                    },
                    label = { Text(stringRes(R.string.tasker_plugin__layout_id)) },
                    supportingText = {
                        Text(stringRes(R.string.tasker_plugin__layout_id_summary))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                )
            }
            TaskerIntentContract.TriggerVoice.ACTION -> {
                TaskerActionOption(
                    selected = voiceMode == "dictation",
                    label = stringRes(R.string.tasker_plugin__voice_dictation),
                    onClick = {
                        voiceMode = "dictation"
                        saveFailed = false
                    },
                )
                TaskerActionOption(
                    selected = voiceMode == "command",
                    label = stringRes(R.string.tasker_plugin__voice_command),
                    onClick = {
                        voiceMode = "command"
                        saveFailed = false
                    },
                )
            }
        }

        if (saveFailed) {
            Text(
                text = stringRes(R.string.tasker_plugin__save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) {
                Text(stringRes(R.string.tasker_plugin__cancel))
            }
            Button(
                enabled = isValid,
                onClick = {
                    saveFailed = !onSave(selectedAction, extras)
                },
            ) {
                Text(stringRes(R.string.tasker_plugin__save))
            }
        }
    }
}

@Composable
private fun TaskerActionOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ToggleRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
