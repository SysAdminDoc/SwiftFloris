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

package dev.patrickgold.florisboard.ime.media.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * ROADMAP §10.5 Next-9.4a — Compose row that renders the user's
 * pinned emoji groups inside the emoji palette.
 *
 * Each group is a chip-style card (subtle rectangular backdrop, 8 dp
 * radius — never a pill per the global no-pill rule) showing the group
 * name plus the first three pinned emoji as a preview. Tapping the
 * chip raises [onGroupTapped] so the palette can commit the group's
 * full emoji list. Long-pressing the chip raises [onGroupLongPressed]
 * for a compact hint or a future management surface.
 *
 * Designed for embed inside the existing `EmojiPaletteView`'s
 * horizontal Pinned strip — the strip lives above the search bar, so
 * this row is intentionally compact (≤56 dp tall) and never wraps.
 *
 * When [groups] is empty the composable emits nothing — the palette
 * row simply disappears, matching the existing "no pinned items"
 * fallback in `EmojiPaletteView`.
 */
@Composable
fun PinnedGroupsPaletteRow(
    groups: List<PinnedGroupChip>,
    onGroupTapped: (groupName: String) -> Unit,
    onGroupLongPressed: (groupName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (group in groups) {
            PinnedGroupChip(
                chip = group,
                onTap = { onGroupTapped(group.name) },
                onLongPress = { onGroupLongPressed(group.name) },
            )
        }
    }
}

@Composable
private fun PinnedGroupChip(
    chip: PinnedGroupChip,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Subtle rectangular backdrop, 8 dp radius — adheres to the
    // global "no pill / oval / fully-rounded backdrop" rule.
    // Colors derive from the active Snygg keyboard theme (tab foreground at
    // low alpha for the backdrop) so the chips work on light themes too,
    // mirroring the PinToGroupSheet treatment.
    var pressed by remember { mutableStateOf(false) }
    val tabStyle = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiTab.elementName)
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val foreground = tabStyle.foreground(default = windowStyle.foreground(default = Color(0xFFE6E6F0)))
    val baseColor = foreground.copy(alpha = 0.10f)
    val pressedColor = foreground.copy(alpha = 0.20f)
    val badgeColor = foreground.copy(alpha = 0.16f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (pressed) pressedColor else baseColor)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = null) {
                    onTap()
                    true
                }
                onLongClick(label = null) {
                    onLongPress()
                    true
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pointerInput(chip.name) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = chip.name, fontSize = 13.sp, color = foreground)
            if (chip.previewEmojis.isNotEmpty()) {
                Text(
                    text = chip.previewEmojis.joinToString(separator = ""),
                    fontSize = 13.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip.totalEmojiCount.toString(),
                    fontSize = 10.sp,
                    color = foreground.copy(alpha = 0.84f),
                )
            }
        }
    }
}

/**
 * Compose-friendly snapshot of one pinned group. Caller-built from
 * [EmojiPinGroupStore]; kept as a data class so the row composable
 * stays pure and trivially testable.
 */
data class PinnedGroupChip(
    val name: String,
    val previewEmojis: List<String>,
    val totalEmojiCount: Int,
) {
    companion object {
        const val PREVIEW_LIMIT: Int = 3

        /** Build chips from a live store snapshot. */
        fun fromStoreSnapshot(snapshot: Map<String, List<String>>): List<PinnedGroupChip> =
            snapshot.entries.map { (name, emojis) ->
                PinnedGroupChip(
                    name = name,
                    previewEmojis = emojis.take(PREVIEW_LIMIT),
                    totalEmojiCount = emojis.size,
                )
            }
    }
}
