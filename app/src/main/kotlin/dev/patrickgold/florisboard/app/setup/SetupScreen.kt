/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.setup

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryScreenAction
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryType
import dev.patrickgold.florisboard.app.settings.localization.displayName
import dev.patrickgold.florisboard.ime.core.BilingualSubtypePresets
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeJsonConfig
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisScreenScope
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.florisboard.app.settings.search.SettingsListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.FlorisStep
import org.florisboard.lib.compose.FlorisStepLayout
import org.florisboard.lib.compose.FlorisStepLayoutScope
import org.florisboard.lib.compose.FlorisStepState
import org.florisboard.lib.compose.FlorisTextButton
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes
import java.text.Collator
import java.util.Locale

@Composable
fun SetupScreen() = FlorisScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
    val hasNotificationPermission by prefs.internal.notificationPermissionState.collectAsState()
    val aiFeaturesExplainerSeen by prefs.internal.aiFeaturesExplainerSeen.collectAsState()
    val firstRunImportHintSeen by prefs.internal.firstRunImportHintSeen.collectAsState()
    // Read from the saved list rather than the subtype manager's copy, which loads in the background and would
    // flash the language step on a profile that already has one.
    val subtypesRaw by prefs.localization.subtypes.collectAsState()
    val hasKeyboardLanguage = remember(subtypesRaw) {
        runCatching { SubtypeJsonConfig.decodeFromString<List<Subtype>>(subtypesRaw).isNotEmpty() }.getOrDefault(false)
    }

    val requestNotification =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            scope.launch {
                if (isGranted) {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.GRANTED)
                } else {
                    prefs.internal.notificationPermissionState.set(NotificationPermissionState.DENIED)
                }
            }
        }

    Content(
        isFlorisBoardEnabled,
        isFlorisBoardSelected,
        context,
        navController,
        requestNotification,
        hasNotificationPermission,
        aiFeaturesExplainerSeen,
        hasKeyboardLanguage,
        firstRunImportHintSeen,
        scope,
    )
}

@Composable
private fun FlorisScreenScope.Content(
    isFlorisBoardEnabled: Boolean,
    isFlorisBoardSelected: Boolean,
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    hasNotificationPermission: NotificationPermissionState,
    aiFeaturesExplainerSeen: Boolean,
    hasKeyboardLanguage: Boolean,
    firstRunImportHintSeen: Boolean,
    scope: CoroutineScope,
) {

    val stepState = rememberSaveable(saver = FlorisStepState.Saver) {
        val initStep = SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = isFlorisBoardEnabled,
            isFlorisBoardSelected = isFlorisBoardSelected,
            notificationPermissionState = hasNotificationPermission,
            aiFeaturesExplainerSeen = aiFeaturesExplainerSeen,
            hasKeyboardLanguage = hasKeyboardLanguage,
            firstRunImportHintSeen = firstRunImportHintSeen,
            supportsNotificationPermission = AndroidVersion.ATLEAST_API33_T,
        ).id
        FlorisStepState.new(init = initStep)
    }

    content {
        LaunchedEffect(
            isFlorisBoardEnabled,
            isFlorisBoardSelected,
            hasNotificationPermission,
            aiFeaturesExplainerSeen,
            hasKeyboardLanguage,
            firstRunImportHintSeen,
        ) {
            stepState.setCurrentAuto(
                SetupStepPolicy.nextStep(
                    isFlorisBoardEnabled = isFlorisBoardEnabled,
                    isFlorisBoardSelected = isFlorisBoardSelected,
                    notificationPermissionState = hasNotificationPermission,
                    aiFeaturesExplainerSeen = aiFeaturesExplainerSeen,
                    hasKeyboardLanguage = hasKeyboardLanguage,
                    firstRunImportHintSeen = firstRunImportHintSeen,
                    supportsNotificationPermission = AndroidVersion.ATLEAST_API33_T,
                ).id
            )
        }

        // Poll the system IME enabler state so we can auto-advance as soon as the
        // user enables SwiftFloris in system settings. The key includes the state
        // parameters so the effect restarts (with fresh captures) whenever they
        // change, avoiding stale-closure reads.
        //
        // Those same captures are why the poll has to stop itself. They stay
        // frozen for the lifetime of one effect run, so once the relaunch fires
        // the loop's condition is still satisfied on the next tick and stays
        // satisfied until the settings observer propagates and restarts the
        // effect. Without the return that window relaunched the activity twice a
        // second. The guard above it skips the loop entirely on the runs where
        // the captured state already rules a relaunch out, instead of polling
        // system settings from the main thread for the rest of the screen's life.
        LaunchedEffect(isFlorisBoardEnabled, isFlorisBoardSelected, hasNotificationPermission) {
            if (isFlorisBoardEnabled ||
                isFlorisBoardSelected ||
                hasNotificationPermission != NotificationPermissionState.NOT_SET
            ) {
                return@LaunchedEffect
            }
            while (true) {
                delay(500L)
                if (stepState.getCurrentAuto().value == SetupStep.EnableIme.id &&
                    stepState.getCurrentManual().value == -1 &&
                    InputMethodUtils.isFlorisboardEnabled(context)
                ) {
                    context.launchActivity(FlorisAppActivity::class) {
                        it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    return@LaunchedEffect
                }
            }
        }
        FlorisStepLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            stepState = stepState,
            header = {
                StepText(stringRes(R.string.setup__intro_message))
                Spacer(modifier = Modifier.height(16.dp))
            },
            steps = steps(
                context, navController, requestNotification, scope, stepState
            ),
            footer = {
                Footer(context)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(context: Context) {
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
            FlorisTextButton(
                onClick = { context.launchUrl(privacyPolicyUrl) },
                text = stringRes(R.string.setup__footer__privacy_policy),
            )
            val repositoryUrl = stringRes(R.string.florisboard__repo_url)
            FlorisTextButton(
                onClick = { context.launchUrl(repositoryUrl) },
                text = stringRes(R.string.setup__footer__repository),
            )
        }
    }
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.steps(
    context: Context,
    navController: NavController,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    scope: CoroutineScope,
    stepState: FlorisStepState,
): List<FlorisStep> {
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    val subtypePresets by keyboardManager.resources.subtypePresets.collectAsState()
    val displayLanguageNamesIn by this@steps.prefs.localization.displayLanguageNamesIn.collectAsState()
    // The keyboard the language step added, so picking again from the reopened step swaps it instead of adding one.
    // It's kept until setup is finished, since leaving setup and coming back starts this screen over.
    val setupLanguageSubtypeId = this@steps.prefs.internal.setupLanguageSubtypeId
    var languageStepSubtypeId by rememberSaveable {
        mutableStateOf(setupLanguageSubtypeId.get().takeIf { it != setupLanguageSubtypeId.default })
    }

    return listOfNotNull(
        FlorisStep(
            id = SetupStep.AiFeatures.id,
            title = stringRes(R.string.setup__ai_features__title),
        ) {
            StepText(stringRes(R.string.setup__ai_features__description_p1))
            StepButton(label = stringRes(R.string.setup__ai_features__acknowledge_btn)) {
                scope.launch { this@steps.prefs.internal.aiFeaturesExplainerSeen.set(true) }
            }
            FlorisTextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = { navController.navigate(Routes.Settings.AiFeatures) },
                text = stringRes(R.string.setup__ai_features__open_details_btn),
            )
        },
        FlorisStep(
            id = SetupStep.ChooseLanguage.id,
            title = stringRes(R.string.setup__choose_language__title),
        ) {
            ChooseLanguageStep(
                presets = subtypePresets,
                displayLanguageNamesIn = displayLanguageNamesIn,
                onChoose = { subtype ->
                    val addedId = subtypeManager.addAndActivateSubtype(subtype, replacing = languageStepSubtypeId)
                    languageStepSubtypeId = addedId
                    // Kept even when the step's screen goes away mid-write (Back, or the next step's navigation).
                    scope.launch(start = CoroutineStart.ATOMIC) {
                        withContext(NonCancellable) {
                            setupLanguageSubtypeId.set(addedId ?: setupLanguageSubtypeId.default)
                        }
                    }
                    // Closes the step when it was reopened by tapping its title. Reached the usual way, the step
                    // moves on by itself once the keyboard is saved.
                    stepState.setCurrentManual(stepState.getCurrentAuto().value)
                },
            )
        },
        FlorisStep(
            id = SetupStep.ImportDictionary.id,
            title = stringRes(R.string.setup__import_hint__title),
        ) {
            StepText(stringRes(R.string.setup__import_hint__description_p1))
            StepText(
                modifier = Modifier.padding(top = 8.dp),
                text = stringRes(R.string.setup__import_hint__description_p2),
            )
            StepButton(label = stringRes(R.string.setup__import_hint__choose_file_btn)) {
                scope.launch { this@steps.prefs.internal.firstRunImportHintSeen.set(true) }
                navController.navigate(
                    Routes.Settings.UserDictionary(
                        type = UserDictionaryType.FLORIS,
                        action = UserDictionaryScreenAction.IMPORT,
                    )
                )
            }
            FlorisTextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = {
                    scope.launch { this@steps.prefs.internal.firstRunImportHintSeen.set(true) }
                },
                text = stringRes(R.string.setup__import_hint__skip_btn),
            )
        },
        FlorisStep(
            id = SetupStep.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
        ) {
            StepText(stringRes(R.string.setup__enable_ime__description))
            StepButton(label = stringRes(R.string.setup__enable_ime__open_settings_btn)) {
                InputMethodUtils.showImeEnablerActivity(context)
            }
        },
        FlorisStep(
            id = SetupStep.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
        ) {
            StepText(stringRes(R.string.setup__select_ime__description))
            StepButton(label = stringRes(R.string.setup__select_ime__switch_keyboard_btn)) {
                InputMethodUtils.showImePicker(context)
            }
        },
        if (AndroidVersion.ATLEAST_API33_T) {
            FlorisStep(
                id = SetupStep.SelectNotification.id,
                title = stringRes(R.string.setup__grant_notification_permission__title),
            ) {
                StepText(stringRes(R.string.setup__grant_notification_permission__description))
                StepButton(stringRes(R.string.setup__grant_notification_permission__btn)) {
                    requestNotification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else null,
        FlorisStep(
            id = SetupStep.FinishUp.id,
            title = stringRes(R.string.setup__finish_up__title),
        ) {
            StepText(stringRes(R.string.setup__finish_up__description_p1))
            StepText(stringRes(R.string.setup__finish_up__description_p2))
            StepButton(label = stringRes(R.string.setup__finish_up__finish_btn)) {
                // The navigation below ends this screen's scope, which would cut the writes off at reset()'s
                // suspension and bring setup back, so they run to the end regardless.
                scope.launch(start = CoroutineStart.ATOMIC) {
                    withContext(NonCancellable) {
                        // A finished setup's language is the user's own, so setup shown again never takes it out.
                        // It's let go first: should this stop between the two, setup comes back and a pick there
                        // only adds.
                        setupLanguageSubtypeId.reset()
                        this@steps.prefs.internal.isImeSetUp.set(true)
                    }
                }
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Setup.Screen) {
                        inclusive = true
                    }
                }
            }
        }
    )
}

/**
 * The phone's languages that have a keyboard, English next to them (or alone when none has one) and a searchable
 * list of every bundled language. Choosing one saves it, which finishes the step.
 */
@Composable
private fun FlorisStepLayoutScope.ChooseLanguageStep(
    presets: List<SubtypePreset>,
    displayLanguageNamesIn: DisplayLanguageNamesIn,
    onChoose: (Subtype) -> Unit,
) {
    val systemLocales = remember { systemLocales() }
    val suggestions = remember(presets, systemLocales) { FirstRunLanguagePolicy.suggestions(systemLocales, presets) }
    val english = remember(presets) { FirstRunLanguagePolicy.englishFallback(presets) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    if (presets.isEmpty()) {
        // The bundled languages are still loading. Should they never load, the keyboard's built-in English (US)
        // still lets setup go on.
        StepText(stringRes(R.string.setup__choose_language__description))
        var presetsLate by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(PresetLoadWaitMillis)
            presetsLate = true
        }
        if (presetsLate) {
            StepButton(label = Subtype.DEFAULT.displayName(displayLanguageNamesIn)) { onChoose(Subtype.DEFAULT) }
        }
        return
    }
    val notice = remember(presets, systemLocales) {
        FirstRunLanguagePolicy.notice(systemLocales.ifEmpty { listOf(Locale.getDefault()) }, presets)
    }
    // The language is named in the same language as the sentence around it, and renamed if that changes.
    val displayLocale = LocalConfiguration.current.locales[0]
    when (notice) {
        is FirstRunLanguagePolicy.Notice.UncommonOnly -> {
            val language = FirstRunLanguagePolicy.missingLanguageName(notice.systemLocale, presets, displayLocale)
            val method = FirstRunLanguagePolicy.uncommonMethodName(notice.preset)
            // "Pick English" only fits when English is the one keyboard shown.
            val text = if (notice.othersSuggested) {
                R.string.setup__choose_language__uncommon_only_with_others
            } else {
                R.string.setup__choose_language__uncommon_only
            }
            StepText(stringRes(text, "language" to language, "method" to method))
        }
        is FirstRunLanguagePolicy.Notice.NoMatch -> {
            val language = FirstRunLanguagePolicy.missingLanguageName(notice.systemLocale, presets, displayLocale)
            StepText(stringRes(R.string.setup__choose_language__no_match, "language" to language))
        }
        FirstRunLanguagePolicy.Notice.Suggested -> StepText(stringRes(R.string.setup__choose_language__description))
    }
    for (preset in suggestions) {
        StepButton(label = preset.displayName(displayLanguageNamesIn)) { onChoose(preset.toSubtype()) }
    }
    if (english != null && english !in suggestions) {
        if (suggestions.isEmpty()) {
            StepButton(label = english.displayName(displayLanguageNamesIn)) { onChoose(english.toSubtype()) }
        } else {
            FlorisOutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = { onChoose(english.toSubtype()) },
                text = english.displayName(displayLanguageNamesIn),
            )
        }
    }
    FlorisTextButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        onClick = { showPicker = true },
        text = stringRes(R.string.setup__choose_language__other_btn),
    )

    if (showPicker) {
        LanguagePickerDialog(
            presets = presets,
            displayLanguageNamesIn = displayLanguageNamesIn,
            onChoose = { preset ->
                showPicker = false
                onChoose(preset.toSubtype())
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    presets: List<SubtypePreset>,
    displayLanguageNamesIn: DisplayLanguageNamesIn,
    onChoose: (SubtypePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboardManager by LocalContext.current.keyboardManager()
    val layouts by keyboardManager.resources.layouts.collectAsState()
    val bilingualSummary = stringRes(R.string.settings__localization__subtype_bilingual_summary)
    val sortedPresets = remember(presets, displayLanguageNamesIn) {
        val collator = Collator.getInstance()
        (BilingualSubtypePresets.canonicalFrom(presets) + presets)
            .sortedWith(compareBy(collator) { it.displayName(displayLanguageNamesIn) })
    }
    val shownPresets = remember(sortedPresets, query) {
        FirstRunLanguagePolicy.search(query, sortedPresets) { preset ->
            FirstRunLanguagePolicy.searchNames(preset, preset.displayName(displayLanguageNamesIn))
        }
    }
    JetPrefAlertDialog(
        title = stringRes(R.string.setup__choose_language__picker_title),
        dismissLabel = stringRes(android.R.string.cancel),
        scrollModifier = Modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        onDismiss = onDismiss,
    ) {
        Column {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                value = query,
                onValueChange = { query = it },
                label = { Text(stringRes(R.string.setup__choose_language__search_label)) },
                singleLine = true,
            )
            HorizontalDivider()
            if (shownPresets.isEmpty()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringRes(R.string.setup__choose_language__search_empty),
                )
            } else {
                val lazyListState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .florisScrollbar(lazyListState, isVertical = true)
                        .weight(1f),
                    state = lazyListState,
                ) {
                    items(shownPresets) { preset ->
                        val layout = layouts[LayoutType.CHARACTERS]?.get(preset.preferred.characters)?.label
                            ?: preset.preferred.characters.componentId
                        SettingsListItem(
                            modifier = Modifier.clickable(role = Role.Button) { onChoose(preset) },
                            text = preset.displayName(displayLanguageNamesIn),
                            secondaryText = if (preset.secondaryLocales.isEmpty()) {
                                layout
                            } else {
                                "$bilingualSummary / $layout"
                            },
                            colors = ListItemDefaults.colors(containerColor = AlertDialogDefaults.containerColor),
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** How long the language step waits for the bundled languages before offering the built-in English one. */
private const val PresetLoadWaitMillis = 3_000L

/** The phone's own languages, in its order. The app's language can differ, so the app's resources won't do. */
private fun systemLocales(): List<Locale> {
    val locales = Resources.getSystem().configuration.locales
    return (0 until locales.size()).map { locales[it] }
}
