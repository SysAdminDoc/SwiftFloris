/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictionary

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

@Composable
fun MigrationAssistantScreen() = FlorisScreen {
    title = stringRes(R.string.settings__migration_assistant__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        FlorisWarningCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.settings__migration_assistant__retired_warning_title),
            secondaryText = stringRes(R.string.settings__migration_assistant__retired_warning_summary),
        )

        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.settings__migration_assistant__intro),
        )

        PreferenceGroup(title = stringRes(R.string.settings__migration_assistant__group_import_paths)) {
            Preference(
                icon = Icons.Default.CloudOff,
                title = stringRes(R.string.settings__migration_assistant__swiftkey_title),
                summary = stringRes(R.string.settings__migration_assistant__swiftkey_summary),
                onClick = {
                    navController.navigate(
                        Routes.Settings.UserDictionary(
                            type = UserDictionaryType.FLORIS,
                            action = UserDictionaryScreenAction.IMPORT,
                        ),
                    )
                },
            )
            Preference(
                icon = Icons.Default.FileOpen,
                title = stringRes(R.string.settings__migration_assistant__gboard_title),
                summary = stringRes(R.string.settings__migration_assistant__gboard_summary),
                onClick = {
                    navController.navigate(
                        Routes.Settings.UserDictionary(
                            type = UserDictionaryType.FLORIS,
                            action = UserDictionaryScreenAction.IMPORT,
                        ),
                    )
                },
            )
            Preference(
                icon = Icons.Default.SwapHoriz,
                title = stringRes(R.string.settings__migration_assistant__florisboard_title),
                summary = stringRes(R.string.settings__migration_assistant__florisboard_summary),
                onClick = {
                    navController.navigate(
                        Routes.Settings.UserDictionary(
                            type = UserDictionaryType.FLORIS,
                            action = UserDictionaryScreenAction.IMPORT,
                        ),
                    )
                },
            )
            Preference(
                icon = Icons.Default.Lock,
                title = stringRes(R.string.settings__migration_assistant__encrypted_title),
                summary = stringRes(R.string.settings__migration_assistant__encrypted_summary),
                // .sfexp is produced and consumed by the personal dictionary
                // screen, not by the archive Restore flow. The import picker
                // detects the encrypted envelope and prompts for the
                // passphrase, so the same IMPORT action serves both cases.
                onClick = {
                    navController.navigate(
                        Routes.Settings.UserDictionary(
                            type = UserDictionaryType.FLORIS,
                            action = UserDictionaryScreenAction.IMPORT,
                        ),
                    )
                },
            )
            Preference(
                icon = Icons.Default.ContentPaste,
                title = stringRes(R.string.settings__migration_assistant__manual_title),
                summary = stringRes(R.string.settings__migration_assistant__manual_summary),
                onClick = {
                    navController.navigate(
                        Routes.Settings.UserDictionary(type = UserDictionaryType.FLORIS),
                    )
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__migration_assistant__group_learn_more)) {
            Preference(
                title = stringRes(R.string.settings__migration_assistant__guide_title),
                summary = stringRes(R.string.settings__migration_assistant__guide_summary),
                onClick = { context.launchUrl(R.string.florisboard__migration_guide_url) },
            )
        }
    }
}
