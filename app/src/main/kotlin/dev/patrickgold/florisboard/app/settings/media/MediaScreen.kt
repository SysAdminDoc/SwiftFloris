/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.media

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistoryHelper
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSkinTone
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.ime.media.sticker.UserStickerRepository
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun MediaScreen() = FlorisScreen {
    title = stringRes(R.string.settings__media__title)
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val userStickerFolderUri by prefs.sticker.userFolderUri.collectAsState()

    var shouldDelete by remember { mutableStateOf<ShouldDelete?>(null) }
    val scope = rememberCoroutineScope()
    val folderSelectedText = stringRes(R.string.prefs__media__stickers_folder_selected)
    val folderClearedText = stringRes(R.string.prefs__media__stickers_folder_cleared)
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch {
                prefs.sticker.userFolderUri.set(uri.toString())
            }
            Toast.makeText(context, folderSelectedText, Toast.LENGTH_SHORT).show()
        }
    }

    content {
        PreferenceGroup(title = stringRes(R.string.prefs__media__emoji_defaults__title)) {
            ListPreference(
                prefs.emoji.preferredSkinTone,
                title = stringRes(R.string.prefs__media__emoji_preferred_skin_tone),
                entries = enumDisplayEntriesOf(EmojiSkinTone::class),
            )
        }

        PreferenceGroup(title = stringRes(R.string.prefs__media__emoji_history__title)) {
            SwitchPreference(
                prefs.emoji.historyEnabled,
                icon = Icons.Outlined.Schedule,
                title = stringRes(R.string.prefs__media__emoji_history_enabled),
                summary = stringRes(R.string.prefs__media__emoji_history_enabled__summary),
            )
            ListPreference(
                prefs.emoji.historyPinnedUpdateStrategy,
                title = stringRes(R.string.prefs__media__emoji_history_pinned_update_strategy),
                entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            ListPreference(
                prefs.emoji.historyRecentUpdateStrategy,
                title = stringRes(R.string.prefs__media__emoji_history_recent_update_strategy),
                entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            DialogSliderPreference(
                primaryPref = prefs.emoji.historyPinnedMaxSize,
                secondaryPref = prefs.emoji.historyRecentMaxSize,
                title = stringRes(R.string.prefs__media__emoji_history_max_size),
                primaryLabel = stringRes(R.string.emoji__history__pinned),
                secondaryLabel = stringRes(R.string.emoji__history__recent),
                valueLabel = { maxSize ->
                    if (maxSize == EmojiHistory.MaxSizeUnlimited) {
                        stringRes(R.string.general__unlimited)
                    } else {
                        pluralsRes(R.plurals.unit__items__written, maxSize, "v" to maxSize)
                    }
                },
                min = 0,
                max = 120,
                stepIncrement = 1,
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            Preference(
                title = stringRes(R.string.prefs__media__emoji_history_pinned_reset),
                onClick = {
                    shouldDelete = ShouldDelete(true)
                },
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )
            Preference(
                title = stringRes(R.string.prefs__media__emoji_history_reset),
                onClick = {
                    shouldDelete = ShouldDelete(false)
                },
                enabledIf = { prefs.emoji.historyEnabled.isTrue() },
            )

        }

        PreferenceGroup(title = stringRes(R.string.prefs__media__emoji_suggestion__title)) {
            SwitchPreference(
                prefs.emoji.suggestionEnabled,
                icon = Icons.Outlined.EmojiSymbols,
                title = stringRes(R.string.prefs__media__emoji_suggestion_enabled),
                summary = stringRes(R.string.prefs__media__emoji_suggestion_enabled__summary),
            )
            ListPreference(
                prefs.emoji.suggestionType,
                title = stringRes(R.string.prefs__media__emoji_suggestion_type),
                entries = enumDisplayEntriesOf(EmojiSuggestionType::class),
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
            SwitchPreference(
                prefs.emoji.suggestionUpdateHistory,
                title = stringRes(R.string.prefs__media__emoji_suggestion_update_history),
                summary = stringRes(R.string.prefs__media__emoji_suggestion_update_history__summary),
                enabledIf = {
                    prefs.emoji.suggestionEnabled.isTrue() && prefs.emoji.historyEnabled.isTrue()
                },
            )
            SwitchPreference(
                prefs.emoji.suggestionCandidateShowName,
                title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name),
                summary = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name__summary),
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
            DialogSliderPreference(
                prefs.emoji.suggestionQueryMinLength,
                title = stringRes(R.string.prefs__media__emoji_suggestion_query_min_length),
                valueLabel = { length ->
                    pluralsRes(R.plurals.unit__characters__written, length, "v" to length)
                },
                min = 1,
                max = 5,
                stepIncrement = 1,
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
            DialogSliderPreference(
                prefs.emoji.suggestionCandidateMaxCount,
                title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_max_count),
                valueLabel = { count ->
                    pluralsRes(R.plurals.unit__candidates__written, count, "v" to count)
                },
                min = 1,
                max = 10,
                stepIncrement = 1,
                enabledIf = { prefs.emoji.suggestionEnabled.isTrue() },
            )
        }

        PreferenceGroup(title = stringRes(R.string.prefs__media__stickers__title)) {
            // Recompute on every recomposition so a system-level grant
            // revocation (e.g. user uninstalled the file-manager that
            // issued the persistable grant) is reflected when the user
            // re-enters this screen. The check is a single in-memory
            // scan of the persistedUriPermissions list; cheap.
            val grantLost = userStickerFolderUri.isNotBlank() &&
                !UserStickerRepository.hasPersistableReadPermission(context, userStickerFolderUri)
            Preference(
                icon = Icons.Default.Image,
                title = stringRes(R.string.prefs__media__stickers_folder),
                summary = when {
                    userStickerFolderUri.isBlank() ->
                        stringRes(R.string.prefs__media__stickers_folder__summary_empty)
                    grantLost ->
                        stringRes(R.string.prefs__media__stickers_folder__summary_grant_lost)
                    else ->
                        stringRes(
                            R.string.prefs__media__stickers_folder__summary_selected,
                            "folder" to userStickerFolderUri.substringAfterLast(':').substringAfterLast('/'),
                        )
                },
                onClick = {
                    folderLauncher.launch(null)
                },
            )
            Preference(
                icon = Icons.Outlined.Delete,
                title = stringRes(R.string.prefs__media__stickers_folder_clear),
                summary = stringRes(R.string.prefs__media__stickers_folder_clear__summary),
                enabledIf = { userStickerFolderUri.isNotBlank() },
                onClick = {
                    scope.launch {
                        prefs.sticker.userFolderUri.set("")
                    }
                    Toast.makeText(context, folderClearedText, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    DeleteEmojiHistoryConfirmDialog(
        shouldDelete = shouldDelete,
        onDismiss = {
            shouldDelete = null
        },
        onConfirm = {
            shouldDelete?.let {
                scope.launch {
                    if (it.pinned) {
                        EmojiHistoryHelper.deletePinned(prefs = prefs)
                    } else {
                        EmojiHistoryHelper.deleteHistory(prefs = prefs)
                    }
                }
                shouldDelete = null
            }
        },
    )
}

@Composable
fun DeleteEmojiHistoryConfirmDialog(
    shouldDelete: ShouldDelete?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    shouldDelete?.let {
        JetPrefAlertDialog(
            title = stringRes(R.string.action__reset_confirm_title),
            confirmLabel = stringRes(R.string.action__yes),
            dismissLabel = stringRes(R.string.action__no),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        ) {
            if (it.pinned) {
                Text(
                    stringRes(
                        R.string.action__reset_confirm_message,
                        "name" to stringRes(R.string.prefs__media__emoji_history_pinned_reset_confirm_name),
                    )
                )
            } else {
                Text(
                    stringRes(
                        R.string.action__reset_confirm_message,
                        "name" to stringRes(R.string.prefs__media__emoji_history_reset_confirm_name),
                    )
                )
            }

        }
    }
}

data class ShouldDelete(val pinned: Boolean)
