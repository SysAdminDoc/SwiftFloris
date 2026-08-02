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

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.provider.Settings
import android.view.InputDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.copyImportDiagnosticsToClipboard
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardLayoutRepository
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardLayoutSourceFormat
import dev.patrickgold.florisboard.ime.hardware.ImportedHardwareKeyboardLayout
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes

@Composable
fun PhysicalKeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.physical_keyboard__title)

    val context = LocalContext.current
    val appContext = context.applicationContext
    val keyboardManager by context.keyboardManager()
    val repository = remember(appContext) { HardwareKeyboardLayoutRepository(appContext) }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val physicalKeyboardAttached = configuration.keyboard != Configuration.KEYBOARD_NOKEYS
    var importedLayouts by remember { mutableStateOf<List<ImportedHardwareKeyboardLayout>>(emptyList()) }
    var attachedDevices by remember { mutableStateOf(appContext.hardwareKeyboardDeviceOptions()) }
    var selectedLayoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeOperation by remember { mutableStateOf<PhysicalKeyboardOperation?>(null) }
    var lastNotice by remember { mutableStateOf<PhysicalKeyboardNotice?>(null) }
    var lastNoticeDetail by remember { mutableStateOf<String?>(null) }
    var appliedLayoutName by rememberSaveable { mutableStateOf<String?>(null) }
    var appliedDeviceName by rememberSaveable { mutableStateOf<String?>(null) }

    val activityForResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun refreshDevices() {
        val updatedDevices = appContext.hardwareKeyboardDeviceOptions()
        attachedDevices = updatedDevices
        selectedDeviceId = PhysicalKeyboardPolicy.defaultSelectedDeviceId(
            devices = updatedDevices,
            currentSelectedDeviceId = selectedDeviceId,
        )
    }

    fun refreshLayoutsAfterMutation(updatedLayouts: List<ImportedHardwareKeyboardLayout>) {
        importedLayouts = updatedLayouts
        selectedLayoutId = PhysicalKeyboardPolicy.defaultSelectedLayoutId(
            layoutIds = updatedLayouts.map { it.id },
            currentSelectedLayoutId = selectedLayoutId,
        )
        if (appliedLayoutName != null && updatedLayouts.none { it.displayName == appliedLayoutName }) {
            appliedLayoutName = null
            appliedDeviceName = null
        }
    }

    val importLayoutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || !PhysicalKeyboardPolicy.canStartImport(activeOperation)) return@rememberLauncherForActivityResult
        scope.launch {
            activeOperation = PhysicalKeyboardOperation.Importing
            lastNotice = null
            lastNoticeDetail = null
            val result = repository.importFromUri(uri)
            val updatedLayouts = repository.layouts()
            refreshLayoutsAfterMutation(updatedLayouts)
            result.importedLayout?.let { selectedLayoutId = it.id }
            lastNotice = PhysicalKeyboardPolicy.importNotice(result.status, result.diagnostics)
            lastNoticeDetail = result.detail ?: result.diagnostics.summary().takeIf { it.isNotBlank() }
            activeOperation = null
        }
    }

    fun deleteSelectedLayout() {
        val layoutId = selectedLayoutId ?: return
        scope.launch {
            activeOperation = PhysicalKeyboardOperation.Deleting
            lastNotice = null
            lastNoticeDetail = null
            val deleted = repository.deleteLayout(layoutId)
            refreshLayoutsAfterMutation(repository.layouts())
            lastNotice = if (deleted) {
                PhysicalKeyboardNotice.DeleteSuccess
            } else {
                PhysicalKeyboardNotice.DeleteFailure
            }
            activeOperation = null
        }
    }

    fun applySelectedLayout() {
        val imported = importedLayouts.firstOrNull { it.id == selectedLayoutId }
        val device = attachedDevices.firstOrNull { it.id == selectedDeviceId }
        if (imported == null || device == null) {
            lastNotice = PhysicalKeyboardNotice.ApplyFailure
            return
        }
        keyboardManager.setHardwareKeyboardLayoutForDevice(device.id, imported.layout)
        appliedLayoutName = imported.displayName
        appliedDeviceName = device.displayName
        lastNotice = PhysicalKeyboardNotice.ApplySuccess
        lastNoticeDetail = null
    }

    LaunchedEffect(repository) {
        refreshLayoutsAfterMutation(repository.layouts())
        refreshDevices()
    }

    content {
        PreferenceGroup(title = stringRes(R.string.physical_keyboard__system_group_title)) {
            if (physicalKeyboardAttached) {
                Preference(
                    title = stringRes(R.string.physical_keyboard__system_settings__title),
                    summary = stringRes(R.string.physical_keyboard__system_settings__summary),
                    onClick = {
                        // Some builds ship no hard-keyboard settings activity at all; a missing
                        // one must report itself rather than take the settings app down.
                        val launched = SystemSettingsLaunchPolicy.launchGuarded {
                            activityForResult.launch(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
                        }
                        lastNotice = SystemSettingsLaunchPolicy.noticeFor(launched, lastNotice)
                        if (launched) lastNoticeDetail = null
                    }
                )
            } else {
                Preference(
                    title = stringRes(R.string.physical_keyboard__system_settings__title),
                    summary = stringRes(R.string.physical_keyboard__system_settings__summary_not_attached),
                )
            }
            SwitchPreference(
                pref = prefs.physicalKeyboard.showOnScreenKeyboard,
                title = stringRes(R.string.physical_keyboard__show_on_screen_keyboard__title),
                summary = stringRes(R.string.physical_keyboard__show_on_screen_keyboard__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.physical_keyboard__custom_layouts__title)) {
            FlorisInfoCard(
                modifier = Modifier.padding(8.dp),
                text = stringRes(R.string.physical_keyboard__custom_layouts__info_title),
                secondaryText = stringRes(R.string.physical_keyboard__custom_layouts__info_summary),
                showIcon = false,
            )
            PhysicalKeyboardNoticeCard(
                notice = PhysicalKeyboardPolicy.resolveNotice(activeOperation, lastNotice),
                detail = lastNoticeDetail,
                appliedLayoutName = appliedLayoutName,
                appliedDeviceName = appliedDeviceName,
            )
            ActionListItem(
                title = stringRes(R.string.physical_keyboard__import_layout__title),
                secondaryText = stringRes(R.string.physical_keyboard__import_layout__summary),
                enabled = PhysicalKeyboardPolicy.canStartImport(activeOperation),
                onClick = { importLayoutLauncher.launch(arrayOf("*/*")) },
            )
            if (importedLayouts.isEmpty()) {
                JetPrefListItem(
                    text = stringRes(R.string.physical_keyboard__custom_layouts__empty),
                    secondaryText = stringRes(R.string.physical_keyboard__custom_layouts__empty_summary),
                )
            } else {
                importedLayouts.forEach { layout ->
                    ImportedLayoutListItem(
                        layout = layout,
                        selected = selectedLayoutId == layout.id,
                        onClick = { selectedLayoutId = layout.id },
                    )
                }
                ActionListItem(
                    title = stringRes(R.string.physical_keyboard__forget_layout__title),
                    secondaryText = stringRes(R.string.physical_keyboard__forget_layout__summary),
                    enabled = PhysicalKeyboardPolicy.canDelete(selectedLayoutId, activeOperation),
                    onClick = { deleteSelectedLayout() },
                    trailing = {
                        IconButton(
                            onClick = { deleteSelectedLayout() },
                            enabled = PhysicalKeyboardPolicy.canDelete(selectedLayoutId, activeOperation),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringRes(R.string.physical_keyboard__forget_layout__title),
                            )
                        }
                    },
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.physical_keyboard__attached_devices__title)) {
            ActionListItem(
                title = stringRes(R.string.physical_keyboard__refresh_devices__title),
                secondaryText = stringRes(R.string.physical_keyboard__refresh_devices__summary),
                enabled = activeOperation == null,
                onClick = { refreshDevices() },
            )
            if (attachedDevices.isEmpty()) {
                FlorisWarningCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.physical_keyboard__attached_devices__empty),
                    secondaryText = stringRes(R.string.physical_keyboard__attached_devices__empty_summary),
                )
            } else {
                attachedDevices.forEach { device ->
                    RadioListItem(
                        onClick = { selectedDeviceId = device.id },
                        selected = selectedDeviceId == device.id,
                        text = device.displayName,
                        secondaryText = stringRes(
                            R.string.physical_keyboard__attached_devices__summary,
                            "device_id" to device.id,
                        ),
                    )
                }
            }
            ActionListItem(
                title = stringRes(R.string.physical_keyboard__apply_layout__title),
                secondaryText = stringRes(R.string.physical_keyboard__apply_layout__summary),
                enabled = PhysicalKeyboardPolicy.canApply(selectedLayoutId, selectedDeviceId, activeOperation),
                onClick = { applySelectedLayout() },
            )
        }
    }
}

@Composable
private fun ImportedLayoutListItem(
    layout: ImportedHardwareKeyboardLayout,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RadioListItem(
        onClick = onClick,
        selected = selected,
        text = layout.displayName,
        secondaryText = stringRes(
            R.string.physical_keyboard__custom_layout__summary,
            "source_format" to stringRes(layout.sourceFormat.labelRes()),
            "locale" to layout.locale.ifBlank { stringRes(R.string.physical_keyboard__custom_layout__locale_unknown) },
            "key_count" to layout.keyCount,
            "source_name" to layout.sourceName,
        ),
    )
}

@Composable
private fun ActionListItem(
    title: String,
    secondaryText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    JetPrefListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.56f)
            .rippleClickable(enabled = enabled, role = Role.Button, onClick = onClick),
        text = title,
        secondaryText = secondaryText,
        trailing = trailing,
    )
}

@Composable
private fun PhysicalKeyboardNoticeCard(
    notice: PhysicalKeyboardNotice,
    detail: String?,
    appliedLayoutName: String?,
    appliedDeviceName: String?,
) {
    val context = LocalContext.current
    when (notice) {
        PhysicalKeyboardNotice.Importing -> FlorisProgressCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__in_progress),
            secondaryText = stringRes(R.string.physical_keyboard__import_layout__in_progress_summary),
        )
        PhysicalKeyboardNotice.DeleteInProgress -> FlorisProgressCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__forget_layout__in_progress),
            secondaryText = stringRes(R.string.physical_keyboard__forget_layout__in_progress_summary),
        )
        PhysicalKeyboardNotice.ImportSuccess -> FlorisSuccessCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__success),
            secondaryText = stringRes(R.string.physical_keyboard__import_layout__success_summary),
        )
        PhysicalKeyboardNotice.ImportSuccessWithWarnings -> FlorisWarningCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__success_with_warnings),
            secondaryText = detail ?: stringRes(R.string.physical_keyboard__import_layout__success_summary),
            actionLabel = detail?.let { stringRes(R.string.import_diagnostics__copy_details) },
            onClick = detail?.let { { copyImportDiagnosticsToClipboard(context, it) } },
        )
        PhysicalKeyboardNotice.ImportUnsupported -> FlorisWarningCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__unsupported),
            secondaryText = stringRes(R.string.physical_keyboard__import_layout__unsupported_summary),
        )
        PhysicalKeyboardNotice.ImportNoLayout -> FlorisWarningCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__no_layout),
            secondaryText = detail ?: stringRes(R.string.physical_keyboard__import_layout__no_layout_summary),
            actionLabel = detail?.let { stringRes(R.string.import_diagnostics__copy_details) },
            onClick = detail?.let { { copyImportDiagnosticsToClipboard(context, it) } },
        )
        PhysicalKeyboardNotice.ImportTooLarge -> FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__too_large),
            secondaryText = detail ?: stringRes(R.string.physical_keyboard__import_layout__too_large_summary),
        )
        PhysicalKeyboardNotice.ImportFailure -> FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__import_layout__failure),
            secondaryText = detail ?: stringRes(R.string.physical_keyboard__import_layout__failure_summary),
        )
        PhysicalKeyboardNotice.ApplySuccess -> FlorisSuccessCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__apply_layout__success),
            secondaryText = stringRes(
                R.string.physical_keyboard__apply_layout__success_summary,
                "layout" to (appliedLayoutName ?: stringRes(R.string.physical_keyboard__custom_layout__unknown)),
                "device" to (appliedDeviceName ?: stringRes(R.string.physical_keyboard__attached_devices__unknown)),
            ),
        )
        PhysicalKeyboardNotice.ApplyFailure -> FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__apply_layout__failure),
            secondaryText = stringRes(R.string.physical_keyboard__apply_layout__failure_summary),
        )
        PhysicalKeyboardNotice.DeleteSuccess -> FlorisSuccessCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__forget_layout__success),
            secondaryText = stringRes(R.string.physical_keyboard__forget_layout__success_summary),
        )
        PhysicalKeyboardNotice.DeleteFailure -> FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__forget_layout__failure),
            secondaryText = stringRes(R.string.physical_keyboard__forget_layout__failure_summary),
        )
        PhysicalKeyboardNotice.SystemSettingsUnavailable -> FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.physical_keyboard__system_settings__unavailable_title),
            secondaryText = stringRes(R.string.physical_keyboard__system_settings__unavailable_summary),
        )
        PhysicalKeyboardNotice.None -> Unit
    }
}

private fun HardwareKeyboardLayoutSourceFormat.labelRes(): Int {
    return when (this) {
        HardwareKeyboardLayoutSourceFormat.KLC -> R.string.physical_keyboard__custom_layout__format_klc
        HardwareKeyboardLayoutSourceFormat.MAC_KEYLAYOUT -> R.string.physical_keyboard__custom_layout__format_keylayout
        HardwareKeyboardLayoutSourceFormat.KEYMAN_LDML_PACKAGE ->
            R.string.physical_keyboard__custom_layout__format_keyman
    }
}

private fun Context.hardwareKeyboardDeviceOptions(): List<HardwareKeyboardDeviceOption> {
    val inputManager = applicationContext.systemService(InputManager::class)
    return inputManager.inputDeviceIds
        .asSequence()
        .mapNotNull { deviceId: Int -> InputDevice.getDevice(deviceId) }
        .filter { device ->
            device.id >= 0 &&
                device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE &&
                device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        }
        .map { device ->
            HardwareKeyboardDeviceOption(
                id = device.id,
                displayName = device.name.ifBlank {
                    "Hardware keyboard ${device.id}"
                },
            )
        }
        .toList()
}
