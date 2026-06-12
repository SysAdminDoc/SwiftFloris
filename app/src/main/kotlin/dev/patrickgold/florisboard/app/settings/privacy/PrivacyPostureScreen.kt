/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfiles
import dev.patrickgold.florisboard.ime.smartcompose.AddonConsentState
import dev.patrickgold.florisboard.ime.voice.ExternalVoiceInputProviderState
import dev.patrickgold.florisboard.ime.voice.VoiceInputManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@Composable
fun PrivacyPostureScreen() = FlorisScreen {
    title = stringRes(R.string.settings__privacy_posture__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore

    val suggestionsEnabled by prefs.suggestion.enabled.collectAsState()
    val nextWordPredictionEnabled by prefs.suggestion.nextWordPrediction.collectAsState()
    val clipboardSuggestionEnabled by prefs.clipboard.suggestionEnabled.collectAsState()
    val clipboardHistoryEnabled by prefs.clipboard.historyEnabled.collectAsState()
    val clipboardHistoryLimit by prefs.clipboard.historySizeLimit.collectAsState()
    val clipboardHistoryLimitEnabled by prefs.clipboard.historySizeLimitEnabled.collectAsState()
    val emojiSuggestionEnabled by prefs.emoji.suggestionEnabled.collectAsState()
    val emojiHistoryEnabled by prefs.emoji.historyEnabled.collectAsState()
    val inputFeedbackAudioEnabled by prefs.inputFeedback.audioEnabled.collectAsState()
    val inputFeedbackHapticEnabled by prefs.inputFeedback.hapticEnabled.collectAsState()
    val glideShowTrail by prefs.glide.showTrail.collectAsState()
    val glideShowPreview by prefs.glide.showPreview.collectAsState()
    val smartbarSharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.collectAsState()
    val smartbarExtendedActionsExpanded by prefs.smartbar.extendedActionsExpanded.collectAsState()
    val smartbarSharedActionsExpandWithAnimation by prefs.smartbar.sharedActionsExpandWithAnimation.collectAsState()
    val systemDictionaryEnabled by prefs.dictionary.enableSystemUserDictionary.collectAsState()
    val florisDictionaryEnabled by prefs.dictionary.enableFlorisUserDictionary.collectAsState()
    val addonSigningPins by prefs.addon.signingCertPins.collectAsState()
    val mcpSigningPins by prefs.mcp.signingCertPins.collectAsState()
    val mcpDisabledDaemons by prefs.mcp.disabledDaemonPackages.collectAsState()
    val perAppKeyboardProfiles by prefs.privacy.perAppKeyboardProfiles.collectAsState()
    val smartComposeConsent by prefs.privacy.smartComposeConsent.collectAsState()
    val translationConsent by prefs.privacy.translationConsent.collectAsState()
    val mcpConsent by prefs.privacy.mcpConsent.collectAsState()

    val declaredInternetPermission = remember(context) {
        isPermissionDeclared(context, Manifest.permission.INTERNET)
    }
    val voiceProviderStatuses = remember(context) {
        VoiceInputManager(context).knownExternalVoiceInputProviderStatuses()
    }
    val currentProfileValues = ProfilePreferenceValues(
        suggestionsEnabled = suggestionsEnabled,
        nextWordPredictionEnabled = nextWordPredictionEnabled,
        clipboardSuggestionEnabled = clipboardSuggestionEnabled,
        clipboardHistoryEnabled = clipboardHistoryEnabled,
        emojiSuggestionEnabled = emojiSuggestionEnabled,
        emojiHistoryEnabled = emojiHistoryEnabled,
        inputFeedbackAudioEnabled = inputFeedbackAudioEnabled,
        inputFeedbackHapticEnabled = inputFeedbackHapticEnabled,
        glideShowTrail = glideShowTrail,
        glideShowPreview = glideShowPreview,
        smartbarSharedActionsExpanded = smartbarSharedActionsExpanded,
        smartbarExtendedActionsExpanded = smartbarExtendedActionsExpanded,
        smartbarSharedActionsExpandWithAnimation = smartbarSharedActionsExpandWithAnimation,
        smartComposeConsent = smartComposeConsent,
        translationConsent = translationConsent,
        mcpConsent = mcpConsent,
    )
    val simpleModeActive = PrivacyPosturePolicy.isSimpleModeActive(currentProfileValues)
    val powerSavingActive = PrivacyPosturePolicy.isPowerSavingActive(currentProfileValues)
    val focusModeActive = PrivacyPosturePolicy.isFocusModeActive(currentProfileValues)
    val fullModeActive = currentProfileValues == PrivacyPosturePolicy.fullModeValues
    val simpleModeAppliedToast = stringRes(R.string.settings__privacy_posture__simple_mode_applied)
    val powerSavingAppliedToast = stringRes(R.string.settings__privacy_posture__power_saving_applied)
    val focusModeAppliedToast = stringRes(R.string.settings__privacy_posture__focus_mode_applied)
    val fullModeRestoredToast = stringRes(R.string.settings__privacy_posture__full_mode_restored)

    content {
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.settings__privacy_posture__intro_title),
            secondaryText = stringRes(R.string.settings__privacy_posture__intro_summary),
        )

        PreferenceGroup(title = stringRes(R.string.settings__privacy_posture__group_status)) {
            Preference(
                icon = Icons.Default.WifiOff,
                title = stringRes(R.string.settings__privacy_posture__network_title),
                summary = if (declaredInternetPermission) {
                    stringRes(R.string.settings__privacy_posture__network_declared)
                } else {
                    stringRes(R.string.settings__privacy_posture__network_absent)
                },
            )
            Preference(
                icon = Icons.Default.TextFields,
                title = stringRes(R.string.settings__privacy_posture__learning_title),
                summary = stringRes(
                    R.string.settings__privacy_posture__learning_summary,
                    "enabled" to PrivacyPosturePolicy.enabledCount(
                        suggestionsEnabled,
                        nextWordPredictionEnabled,
                        systemDictionaryEnabled,
                        florisDictionaryEnabled,
                    ),
                    "total" to 4,
                ),
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            Preference(
                icon = Icons.AutoMirrored.Outlined.Assignment,
                title = stringRes(R.string.settings__privacy_posture__clipboard_title),
                summary = clipboardRetentionSummary(
                    historyEnabled = clipboardHistoryEnabled,
                    limitEnabled = clipboardHistoryLimitEnabled,
                    limit = clipboardHistoryLimit,
                    suggestionsEnabled = clipboardSuggestionEnabled,
                ),
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            Preference(
                icon = Icons.Default.SentimentSatisfiedAlt,
                title = stringRes(R.string.settings__privacy_posture__emoji_title),
                summary = stringRes(
                    R.string.settings__privacy_posture__emoji_summary,
                    "enabled" to PrivacyPosturePolicy.enabledCount(emojiHistoryEnabled, emojiSuggestionEnabled),
                    "total" to 2,
                ),
                onClick = { navController.navigate(Routes.Settings.Media) },
            )
            Preference(
                icon = Icons.Default.Extension,
                title = stringRes(R.string.settings__privacy_posture__addons_title),
                summary = addonPermissionSummary(
                    addonPins = addonSigningPins,
                    mcpPins = mcpSigningPins,
                    disabledDaemons = mcpDisabledDaemons,
                    smartComposeConsent = smartComposeConsent,
                    translationConsent = translationConsent,
                    mcpConsent = mcpConsent,
                ),
                onClick = { navController.navigate(Routes.Settings.Addons) },
            )
            Preference(
                icon = Icons.Default.Mic,
                title = stringRes(R.string.settings__privacy_posture__voice_title),
                summary = voiceProviderSummary(voiceProviderStatuses),
                onClick = { navController.navigate(Routes.Settings.VoiceInput) },
            )
            Preference(
                icon = Icons.Default.ContentCopy,
                title = stringRes(R.string.settings__privacy_posture__export_policy_title),
                summary = stringRes(R.string.settings__privacy_posture__export_policy_summary),
                onClick = { navController.navigate(Routes.Settings.Backup) },
            )
            Preference(
                icon = Icons.Default.History,
                title = stringRes(R.string.settings__privacy_audit__title),
                summary = stringRes(R.string.settings__privacy_audit__home_summary),
                onClick = { navController.navigate(Routes.Settings.PrivacyAuditLog) },
            )
            Preference(
                icon = Icons.Default.Apps,
                title = stringRes(R.string.settings__privacy_posture__per_app_profiles_title),
                summary = stringRes(
                    R.string.settings__privacy_posture__per_app_profiles_summary,
                    "count" to PerAppKeyboardProfiles.count(perAppKeyboardProfiles),
                ),
                onClick = { navController.navigate(Routes.Settings.PerAppKeyboardProfiles) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__privacy_posture__group_profiles)) {
            Preference(
                icon = Icons.Default.Shield,
                title = stringRes(R.string.settings__privacy_posture__simple_mode_title),
                summary = if (simpleModeActive) {
                    stringRes(R.string.settings__privacy_posture__simple_mode_active)
                } else {
                    stringRes(R.string.settings__privacy_posture__simple_mode_summary)
                },
                enabledIf = { !simpleModeActive },
                onClick = {
                    scope.launch {
                        applyProfileValues(prefs, PrivacyPosturePolicy.simpleModeValues)
                        Toast.makeText(context, simpleModeAppliedToast, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Preference(
                icon = Icons.Default.WifiOff,
                title = stringRes(R.string.settings__privacy_posture__power_saving_title),
                summary = if (powerSavingActive) {
                    stringRes(R.string.settings__privacy_posture__power_saving_active)
                } else {
                    stringRes(R.string.settings__privacy_posture__power_saving_summary)
                },
                enabledIf = { !powerSavingActive },
                onClick = {
                    scope.launch {
                        applyProfileValues(prefs, PrivacyPosturePolicy.powerSavingValues)
                        Toast.makeText(context, powerSavingAppliedToast, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Preference(
                icon = Icons.Default.TextFields,
                title = stringRes(R.string.settings__privacy_posture__focus_mode_title),
                summary = if (focusModeActive) {
                    stringRes(R.string.settings__privacy_posture__focus_mode_active)
                } else {
                    stringRes(R.string.settings__privacy_posture__focus_mode_summary)
                },
                enabledIf = { !focusModeActive },
                onClick = {
                    scope.launch {
                        applyProfileValues(prefs, PrivacyPosturePolicy.focusModeValues)
                        Toast.makeText(context, focusModeAppliedToast, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Preference(
                icon = Icons.Default.Restore,
                title = stringRes(R.string.settings__privacy_posture__restore_full_title),
                summary = stringRes(R.string.settings__privacy_posture__restore_full_summary),
                enabledIf = { !fullModeActive },
                onClick = {
                    scope.launch {
                        applyProfileValues(prefs, PrivacyPosturePolicy.fullModeValues)
                        Toast.makeText(context, fullModeRestoredToast, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

private suspend fun applyProfileValues(
    prefs: dev.patrickgold.florisboard.app.FlorisPreferenceModel,
    values: ProfilePreferenceValues,
) {
    prefs.suggestion.enabled.set(values.suggestionsEnabled)
    prefs.suggestion.nextWordPrediction.set(values.nextWordPredictionEnabled)
    prefs.clipboard.suggestionEnabled.set(values.clipboardSuggestionEnabled)
    prefs.clipboard.historyEnabled.set(values.clipboardHistoryEnabled)
    prefs.emoji.suggestionEnabled.set(values.emojiSuggestionEnabled)
    prefs.emoji.historyEnabled.set(values.emojiHistoryEnabled)
    prefs.inputFeedback.audioEnabled.set(values.inputFeedbackAudioEnabled)
    prefs.inputFeedback.hapticEnabled.set(values.inputFeedbackHapticEnabled)
    prefs.glide.showTrail.set(values.glideShowTrail)
    prefs.glide.showPreview.set(values.glideShowPreview)
    prefs.smartbar.sharedActionsExpanded.set(values.smartbarSharedActionsExpanded)
    prefs.smartbar.extendedActionsExpanded.set(values.smartbarExtendedActionsExpanded)
    prefs.smartbar.sharedActionsExpandWithAnimation.set(values.smartbarSharedActionsExpandWithAnimation)
    prefs.privacy.smartComposeConsent.set(values.smartComposeConsent)
    prefs.privacy.translationConsent.set(values.translationConsent)
    prefs.privacy.mcpConsent.set(values.mcpConsent)
}

private fun isPermissionDeclared(context: Context, permission: String): Boolean {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    }
    return packageInfo.requestedPermissions?.contains(permission) == true
}

@Composable
private fun clipboardRetentionSummary(
    historyEnabled: Boolean,
    limitEnabled: Boolean,
    limit: Int,
    suggestionsEnabled: Boolean,
): String {
    return when {
        historyEnabled && limitEnabled -> stringRes(
            R.string.settings__privacy_posture__clipboard_limited,
            "limit" to limit,
            "suggestions" to onOffLabel(suggestionsEnabled),
        )
        historyEnabled -> stringRes(
            R.string.settings__privacy_posture__clipboard_unlimited,
            "suggestions" to onOffLabel(suggestionsEnabled),
        )
        else -> stringRes(
            R.string.settings__privacy_posture__clipboard_off,
            "suggestions" to onOffLabel(suggestionsEnabled),
        )
    }
}

@Composable
private fun addonPermissionSummary(
    addonPins: String,
    mcpPins: String,
    disabledDaemons: String,
    smartComposeConsent: AddonConsentState,
    translationConsent: AddonConsentState,
    mcpConsent: AddonConsentState,
): String {
    val pins = countNonBlankLines(addonPins) + countNonBlankLines(mcpPins)
    val disabled = countNonBlankLines(disabledDaemons)
    val granted = PrivacyPosturePolicy.grantedAddonSurfaceCount(
        smartComposeConsent,
        translationConsent,
        mcpConsent,
    )
    return stringRes(
        R.string.settings__privacy_posture__addons_summary,
        "granted" to granted,
        "surfaces" to 3,
        "pins" to pins,
        "disabled" to disabled,
    )
}

@Composable
private fun voiceProviderSummary(
    statuses: List<dev.patrickgold.florisboard.ime.voice.ExternalVoiceInputProviderStatus>,
): String {
    val ready = statuses.firstOrNull { it.state == ExternalVoiceInputProviderState.Ready }
    if (ready != null) {
        return stringRes(
            R.string.settings__privacy_posture__voice_ready,
            "provider" to ready.provider.label,
        )
    }
    val installed = statuses.count {
        it.state == ExternalVoiceInputProviderState.EnabledNeedsMicrophone ||
            it.state == ExternalVoiceInputProviderState.InstalledNotEnabled
    }
    return if (installed > 0) {
        stringRes(R.string.settings__privacy_posture__voice_installed, "count" to installed)
    } else {
        stringRes(R.string.settings__privacy_posture__voice_missing)
    }
}

@Composable
private fun onOffLabel(value: Boolean): String =
    stringRes(if (value) R.string.settings__privacy_posture__on else R.string.settings__privacy_posture__off)

private fun countNonBlankLines(value: String): Int = value.lineSequence().count { it.isNotBlank() }
