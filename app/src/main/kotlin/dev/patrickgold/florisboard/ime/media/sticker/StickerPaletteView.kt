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

package dev.patrickgold.florisboard.ime.media.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import java.util.Locale
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

private val StickerBaseWidth = 96.dp

@Composable
fun StickerPaletteView(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val editorInstance by context.editorInstance()
    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
    val inputFeedbackController = LocalInputFeedbackController.current
    val scope = rememberCoroutineScope()
    val packs = remember { BundledStickerRepository.packs }
    var activePackIndex by remember { mutableIntStateOf(0) }
    val activePack = packs[activePackIndex.coerceIn(packs.indices)]
    val canInsertStickers = remember(activeEditorInfo.contentMimeTypes.toList()) {
        editorInstance.canCommitMimeType(BundledStickerRepository.MimeType)
    }

    Column(modifier = modifier) {
        StickerPackTabRow(
            packs = packs,
            selectedIndex = activePackIndex,
            onSelected = { index ->
                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                activePackIndex = index
            },
        )
        if (!canInsertStickers) {
            SnyggText(
                elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                text = stringRes(R.string.sticker__unsupported_message),
            )
        }
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .florisScrollbar(gridState),
            columns = GridCells.Adaptive(minSize = StickerBaseWidth),
            state = gridState,
        ) {
            items(activePack.stickers, key = { sticker -> sticker.id }) { sticker ->
                StickerTile(
                    sticker = sticker,
                    enabled = canInsertStickers,
                    onTap = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        val committed = editorInstance.commitRichContent(
                            uri = StickerMediaProvider.uriFor(sticker),
                            mimeTypes = listOf(BundledStickerRepository.MimeType),
                            descriptionLabel = sticker.label,
                        )
                        if (!committed) {
                            scope.launch {
                                context.showShortToast(R.string.sticker__commit_failed)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StickerPackTabRow(
    packs: List<StickerPack>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val style = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiTab.elementName)
    PrimaryTabRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = style.foreground(),
        indicator = {
            val focusStyle = rememberSnyggThemeQuery(
                elementName = FlorisImeUi.MediaEmojiTab.elementName,
                selector = SnyggSelector.FOCUS,
            )
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(selectedIndex),
                height = 4.dp,
                color = focusStyle.foreground(),
            )
        },
    ) {
        packs.forEachIndexed { index, pack ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                text = {
                    SnyggText(
                        elementName = FlorisImeUi.MediaEmojiTab.elementName,
                        selector = if (selectedIndex == index) SnyggSelector.FOCUS else SnyggSelector.NONE,
                        text = pack.name,
                    )
                },
            )
        }
    }
}

@Composable
private fun StickerTile(
    sticker: Sticker,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(sticker.backgroundColor))
            .pointerInput(enabled, sticker.id) {
                detectTapGestures(
                    onTap = {
                        if (enabled) onTap()
                    },
                )
            },
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = sticker.emoji,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            text = sticker.label.uppercase(Locale.ROOT),
            color = Color(sticker.textColor),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
