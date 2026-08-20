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

package dev.patrickgold.florisboard.app.settings.privacy

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.profile.PerAppBooleanOverride
import dev.patrickgold.florisboard.ime.profile.PerAppGestureSet
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfile
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfiles
import dev.patrickgold.florisboard.ime.profile.PerAppSuggestionAggressiveness
import dev.patrickgold.florisboard.ime.profile.PerAppThemeOverride
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisSnackbarController
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefDropdown
import dev.patrickgold.jetpref.material.ui.JetPrefDropdownMenuDefaults
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

private data class ProfileDialogSeed(
    val originalPackageName: String?,
    val profile: PerAppKeyboardProfile,
)

@Composable
fun PerAppKeyboardProfileScreen() = FlorisScreen {
    title = stringRes(R.string.settings__per_app_keyboard_profiles__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val deletedMessage = stringRes(R.string.settings__per_app_keyboard_profiles__deleted)
    val undoLabel = stringRes(R.string.action__undo)
    val scope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore
    val editorInstance by context.editorInstance()
    val activeInfo by editorInstance.activeInfoFlow.collectAsState()
    val rawProfiles by prefs.privacy.perAppKeyboardProfiles.collectAsState()
    val profiles = remember(rawProfiles) {
        PerAppKeyboardProfiles.parse(rawProfiles).toSortedMap()
    }
    val activePackageName = remember(activeInfo.packageName, context.packageName) {
        activeInfo.packageName
            ?.trim()
            ?.takeIf { it != context.packageName && PerAppKeyboardProfiles.isRecordablePackageName(it) }
    }
    val activePackageLabel = remember(activePackageName) {
        activePackageName?.let { resolvePackageLabel(context, it) ?: it }
    }
    val malformedStoredProfiles = remember(rawProfiles, profiles) {
        rawProfiles.isNotBlank() &&
            rawProfiles.trim() != PerAppKeyboardProfiles.EmptyJson &&
            profiles.isEmpty()
    }
    var dialogSeed by remember { mutableStateOf<ProfileDialogSeed?>(null) }

    content {
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.settings__per_app_keyboard_profiles__intro_title),
            secondaryText = stringRes(R.string.settings__per_app_keyboard_profiles__intro_summary),
        )
        if (malformedStoredProfiles) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.settings__per_app_keyboard_profiles__malformed_title),
                secondaryText = stringRes(R.string.settings__per_app_keyboard_profiles__malformed_summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__per_app_keyboard_profiles__group_add)) {
            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.settings__per_app_keyboard_profiles__add_current_app),
                summary = activePackageName?.let {
                    stringRes(
                        R.string.settings__per_app_keyboard_profiles__add_current_app_summary,
                        "app" to (activePackageLabel ?: it),
                        "package" to it,
                    )
                } ?: stringRes(R.string.settings__per_app_keyboard_profiles__add_current_app_unavailable),
                enabledIf = { activePackageName != null },
                onClick = {
                    activePackageName?.let { packageName ->
                        dialogSeed = ProfileDialogSeed(
                            originalPackageName = null,
                            profile = profiles[packageName] ?: PerAppKeyboardProfile(
                                packageName = packageName,
                                label = activePackageLabel ?: packageName,
                            ),
                        )
                    }
                },
            )
            Preference(
                icon = Icons.Default.Edit,
                title = stringRes(R.string.settings__per_app_keyboard_profiles__add_package),
                summary = stringRes(R.string.settings__per_app_keyboard_profiles__add_package_summary),
                onClick = {
                    dialogSeed = ProfileDialogSeed(
                        originalPackageName = null,
                        profile = PerAppKeyboardProfile(packageName = ""),
                    )
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__per_app_keyboard_profiles__group_profiles)) {
            if (profiles.isEmpty()) {
                FlorisEmptyState(
                    modifier = Modifier.padding(16.dp),
                    icon = Icons.Default.Apps,
                    title = stringRes(R.string.settings__per_app_keyboard_profiles__empty_title),
                    message = stringRes(R.string.settings__per_app_keyboard_profiles__empty_summary),
                    actionLabel = stringRes(R.string.settings__per_app_keyboard_profiles__add_package),
                    onAction = {
                        dialogSeed = ProfileDialogSeed(
                            originalPackageName = null,
                            profile = PerAppKeyboardProfile(packageName = ""),
                        )
                    },
                )
            } else {
                profiles.values.forEach { profile ->
                    val displayLabel = remember(profile.packageName, profile.label) {
                        profile.label.ifBlank { resolvePackageLabel(context, profile.packageName) ?: profile.packageName }
                    }
                    Preference(
                        icon = Icons.Default.PrivacyTip,
                        title = displayLabel,
                        summary = profileSummary(profile),
                        onClick = {
                            dialogSeed = ProfileDialogSeed(
                                originalPackageName = profile.packageName,
                                profile = profile.copy(label = displayLabel),
                            )
                        },
                    )
                }
            }
        }
    }

    dialogSeed?.let { seed ->
        PerAppKeyboardProfileDialog(
            seed = seed,
            resolveLabel = { packageName -> resolvePackageLabel(context, packageName) },
            onDismiss = { dialogSeed = null },
            onDelete = { packageName ->
                val deletedProfile = profiles[packageName]
                scope.launch {
                    prefs.privacy.perAppKeyboardProfiles.set(
                        PerAppKeyboardProfiles.remove(rawProfiles, packageName),
                    )
                }
                dialogSeed = null
                if (deletedProfile != null) {
                    FlorisSnackbarController.show(
                        message = deletedMessage,
                        actionLabel = undoLabel,
                        onAction = {
                            scope.launch {
                                prefs.privacy.perAppKeyboardProfiles.set(
                                    PerAppKeyboardProfiles.upsert(rawProfiles, deletedProfile),
                                )
                            }
                        },
                    )
                }
            },
            onSave = { originalPackageName, profile ->
                scope.launch {
                    val withoutOld = if (
                        originalPackageName != null &&
                        originalPackageName != profile.packageName
                    ) {
                        PerAppKeyboardProfiles.remove(rawProfiles, originalPackageName)
                    } else {
                        rawProfiles
                    }
                    prefs.privacy.perAppKeyboardProfiles.set(
                        PerAppKeyboardProfiles.upsert(withoutOld, profile),
                    )
                }
                dialogSeed = null
            },
        )
    }
}

@Composable
private fun PerAppKeyboardProfileDialog(
    seed: ProfileDialogSeed,
    resolveLabel: (String) -> String?,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onSave: (String?, PerAppKeyboardProfile) -> Unit,
) {
    val isEditing = seed.originalPackageName != null
    var packageName by remember(seed) { mutableStateOf(seed.profile.packageName) }
    var label by remember(seed) { mutableStateOf(seed.profile.label) }
    var theme by remember(seed) { mutableStateOf(seed.profile.theme) }
    var incognito by remember(seed) { mutableStateOf(seed.profile.incognito) }
    var clipboardHistory by remember(seed) { mutableStateOf(seed.profile.clipboardHistory) }
    var suggestions by remember(seed) { mutableStateOf(seed.profile.suggestions) }
    var gestureSet by remember(seed) { mutableStateOf(seed.profile.gestureSet) }
    val normalizedPackageName = packageName.trim()
    val validPackageName = PerAppKeyboardProfiles.isRecordablePackageName(normalizedPackageName)
    val showPackageError = normalizedPackageName.isNotEmpty() && !validPackageName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringRes(
                if (isEditing) {
                    R.string.settings__per_app_keyboard_profiles__dialog_edit_title
                } else {
                    R.string.settings__per_app_keyboard_profiles__dialog_add_title
                },
            ))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(text = stringRes(R.string.settings__per_app_keyboard_profiles__package_label)) },
                    singleLine = true,
                    isError = showPackageError,
                    supportingText = {
                        Text(text = if (showPackageError) {
                            stringRes(R.string.settings__per_app_keyboard_profiles__package_invalid)
                        } else {
                            stringRes(R.string.settings__per_app_keyboard_profiles__package_supporting)
                        })
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(text = stringRes(R.string.settings__per_app_keyboard_profiles__label_label)) },
                    singleLine = true,
                    supportingText = {
                        Text(text = stringRes(R.string.settings__per_app_keyboard_profiles__label_supporting))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                ProfileDropdown(
                    title = stringRes(R.string.settings__per_app_keyboard_profiles__theme_label),
                    value = theme,
                    values = PerAppThemeOverride.entries,
                    label = { it.label() },
                    onChange = { theme = it },
                )
                ProfileDropdown(
                    title = stringRes(R.string.settings__per_app_keyboard_profiles__incognito_label),
                    value = incognito,
                    values = PerAppBooleanOverride.entries,
                    label = { it.booleanLabel() },
                    onChange = { incognito = it },
                )
                ProfileDropdown(
                    title = stringRes(R.string.settings__per_app_keyboard_profiles__clipboard_label),
                    value = clipboardHistory,
                    values = PerAppBooleanOverride.entries,
                    label = { it.clipboardLabel() },
                    onChange = { clipboardHistory = it },
                )
                ProfileDropdown(
                    title = stringRes(R.string.settings__per_app_keyboard_profiles__suggestions_label),
                    value = suggestions,
                    values = PerAppSuggestionAggressiveness.entries,
                    label = { it.label() },
                    onChange = { suggestions = it },
                )
                ProfileDropdown(
                    title = stringRes(R.string.settings__per_app_keyboard_profiles__gesture_label),
                    value = gestureSet,
                    values = PerAppGestureSet.entries,
                    label = { it.label() },
                    onChange = { gestureSet = it },
                )
                seed.originalPackageName?.let { packageName ->
                    TextButton(
                        onClick = { onDelete(packageName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringRes(R.string.settings__per_app_keyboard_profiles__delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = validPackageName,
                onClick = {
                    val resolvedLabel = label.trim()
                        .ifBlank { resolveLabel(normalizedPackageName) }
                        ?.ifBlank { null }
                        ?: normalizedPackageName
                    onSave(
                        seed.originalPackageName,
                        PerAppKeyboardProfile(
                            packageName = normalizedPackageName,
                            label = resolvedLabel,
                            theme = theme,
                            incognito = incognito,
                            clipboardHistory = clipboardHistory,
                            suggestions = suggestions,
                            gestureSet = gestureSet,
                        ),
                    )
                },
            ) {
                Text(text = stringRes(R.string.action__save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringRes(R.string.action__cancel))
            }
        },
    )
}

@Composable
private fun <T> ProfileDropdown(
    title: String,
    value: T,
    values: List<T>,
    label: @Composable (T) -> String,
    onChange: (T) -> Unit,
) {
    Column {
        Text(
            modifier = Modifier.padding(bottom = 6.dp),
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        val expanded = remember { mutableStateOf(false) }
        val labels = values.map { label(it) }
        JetPrefDropdown(
            options = labels,
            expanded = expanded,
            selectedOptionIndex = values.indexOf(value).coerceAtLeast(0),
            onSelectOption = { index -> onChange(values[index]) },
            appearance = JetPrefDropdownMenuDefaults.outlined(shape = ShapeDefaults.Small),
        )
    }
}

@Composable
private fun profileSummary(profile: PerAppKeyboardProfile): String {
    return stringRes(
        R.string.settings__per_app_keyboard_profiles__profile_summary,
        "package" to profile.packageName,
        "theme" to profile.theme.label(),
        "incognito" to profile.incognito.booleanLabel(),
        "clipboard" to profile.clipboardHistory.clipboardLabel(),
        "suggestions" to profile.suggestions.label(),
        "gestures" to profile.gestureSet.label(),
    )
}

@Composable
private fun PerAppThemeOverride.label(): String = stringRes(when (this) {
    PerAppThemeOverride.FOLLOW_GLOBAL -> R.string.settings__per_app_keyboard_profiles__theme_follow_global
    PerAppThemeOverride.ADAPTIVE_ACCENT -> R.string.settings__per_app_keyboard_profiles__theme_adaptive
    PerAppThemeOverride.GLOBAL_ACCENT -> R.string.settings__per_app_keyboard_profiles__theme_global
})

@Composable
private fun PerAppBooleanOverride.booleanLabel(): String = stringRes(when (this) {
    PerAppBooleanOverride.FOLLOW_GLOBAL -> R.string.settings__per_app_keyboard_profiles__override_follow_global
    PerAppBooleanOverride.FORCE_OFF -> R.string.settings__per_app_keyboard_profiles__override_force_off
    PerAppBooleanOverride.FORCE_ON -> R.string.settings__per_app_keyboard_profiles__override_force_on
})

@Composable
private fun PerAppBooleanOverride.clipboardLabel(): String = stringRes(when (this) {
    PerAppBooleanOverride.FOLLOW_GLOBAL -> R.string.settings__per_app_keyboard_profiles__clipboard_follow_global
    PerAppBooleanOverride.FORCE_OFF -> R.string.settings__per_app_keyboard_profiles__clipboard_force_off
    PerAppBooleanOverride.FORCE_ON -> R.string.settings__per_app_keyboard_profiles__clipboard_force_on
})

@Composable
private fun PerAppSuggestionAggressiveness.label(): String = stringRes(when (this) {
    PerAppSuggestionAggressiveness.FOLLOW_GLOBAL -> R.string.settings__per_app_keyboard_profiles__suggestions_follow_global
    PerAppSuggestionAggressiveness.OFF -> R.string.settings__per_app_keyboard_profiles__suggestions_off
    PerAppSuggestionAggressiveness.CONSERVATIVE -> R.string.settings__per_app_keyboard_profiles__suggestions_conservative
    PerAppSuggestionAggressiveness.BALANCED -> R.string.settings__per_app_keyboard_profiles__suggestions_balanced
    PerAppSuggestionAggressiveness.AGGRESSIVE -> R.string.settings__per_app_keyboard_profiles__suggestions_aggressive
})

@Composable
private fun PerAppGestureSet.label(): String = stringRes(when (this) {
    PerAppGestureSet.FOLLOW_GLOBAL -> R.string.settings__per_app_keyboard_profiles__gesture_follow_global
    PerAppGestureSet.DEFAULT -> R.string.settings__per_app_keyboard_profiles__gesture_default
    PerAppGestureSet.CHAT -> R.string.settings__per_app_keyboard_profiles__gesture_chat
    PerAppGestureSet.CODE -> R.string.settings__per_app_keyboard_profiles__gesture_code
    PerAppGestureSet.READING -> R.string.settings__per_app_keyboard_profiles__gesture_reading
})

private fun resolvePackageLabel(context: Context, packageName: String): String? {
    val packageManager = context.packageManager
    return runCatching {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrNull()
}
