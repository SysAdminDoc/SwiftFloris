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

package dev.patrickgold.florisboard.app.settings.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.media.emoji.CustomEmojiTagStore
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes

@Composable
fun CustomEmojiTagManagerDialog(
    visible: Boolean,
    store: CustomEmojiTagStore,
    revision: Int,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val entries = remember(revision) {
        store.snapshot().entries
            .sortedBy { it.key }
            .map { it.key to it.value.toList() }
    }
    JetPrefAlertDialog(
        title = stringRes(R.string.prefs__media__emoji_tags__dialog_title),
        confirmLabel = stringRes(R.string.action__done),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        if (entries.isEmpty()) {
            Text(text = stringRes(R.string.prefs__media__emoji_tags__empty))
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { (emoji, _) -> emoji }) { (emoji, tags) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = emoji,
                        )
                        for (tag in tags) {
                            val removeLabel = stringRes(
                                R.string.emoji__custom_tag__remove,
                                "tag" to tag,
                                "emoji" to emoji,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = removeLabel
                                    }
                                    .rippleClickable(role = Role.Button) {
                                        store.removeTag(emoji, tag)
                                        onChanged()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = tag,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = removeLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
