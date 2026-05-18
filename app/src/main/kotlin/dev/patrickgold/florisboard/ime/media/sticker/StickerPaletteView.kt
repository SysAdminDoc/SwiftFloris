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

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

private val StickerBaseWidth = 96.dp

// Maximum input dimension we'll accept from a SAF-imported sticker before
// rejecting outright. A legitimate sticker is at most a few thousand pixels
// on a side; anything larger is either a screenshot the user mis-categorised
// or a hostile/corrupted file. Defends the IME process against
// decoder-driven OOM crashes (BitmapFactory.decodeStream with no
// inJustDecodeBounds pre-check would otherwise allocate width * height * 4
// bytes — a 100k x 100k PNG is 40 GB).
private const val MAX_STICKER_DIMENSION = 8192

// Target longest edge for an on-keyboard sticker tile. We downsample so a
// loaded `ImageBitmap` never holds more than a few MB regardless of source
// resolution.
private const val TARGET_STICKER_EDGE_PX = 512

@Composable
fun StickerPaletteView(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val editorInstance by context.editorInstance()
    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
    val inputFeedbackController = LocalInputFeedbackController.current
    val scope = rememberCoroutineScope()
    val userStickerFolderUri by prefs.sticker.userFolderUri.collectAsState()
    var userStickerPack by remember { mutableStateOf<StickerPack?>(null) }
    LaunchedEffect(context, userStickerFolderUri) {
        userStickerPack = withContext(Dispatchers.IO) {
            UserStickerRepository.loadPack(context, userStickerFolderUri)
        }
    }
    val packs = remember(userStickerPack) {
        BundledStickerRepository.packs + listOfNotNull(userStickerPack)
    }
    var activePackIndex by remember { mutableIntStateOf(0) }
    val selectedPackIndex = activePackIndex.coerceIn(packs.indices)
    val activePack = packs[selectedPackIndex]
    val canInsertStickers = remember(activeEditorInfo.contentMimeTypes.toList(), packs) {
        packs.any { pack -> pack.stickers.any { sticker -> sticker.canCommitInEditor(editorInstance) } }
    }

    Column(modifier = modifier) {
        StickerPackTabRow(
            packs = packs,
            selectedIndex = selectedPackIndex,
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
                    enabled = sticker.canCommitInEditor(editorInstance),
                    onTap = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        val committed = editorInstance.commitRichContent(
                            uri = StickerMediaProvider.uriFor(sticker),
                            mimeTypes = sticker.commitMimeTypes,
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
        StickerPreview(sticker = sticker)
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

@Composable
private fun BoxScope.StickerPreview(sticker: Sticker) {
    val sourceUri = sticker.sourceUri
    if (sourceUri == null) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = sticker.emoji,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
        return
    }

    val context = LocalContext.current
    var bitmap by remember(sourceUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(sourceUri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = Uri.parse(sourceUri)
                // Two-pass decode with bounds gate: a hostile / corrupted file
                // returned by the SAF folder could otherwise allocate
                // gigabytes (e.g. a 100k x 100k PNG) and crash the IME
                // process. First pass reads bounds only, then we reject
                // anything past a sanity ceiling and downsample the rest so
                // a single tile never exceeds ~512 px on its longest edge.
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }
                val srcWidth = boundsOptions.outWidth
                val srcHeight = boundsOptions.outHeight
                if (srcWidth <= 0 || srcHeight <= 0) return@runCatching null
                if (srcWidth > MAX_STICKER_DIMENSION || srcHeight > MAX_STICKER_DIMENSION) {
                    return@runCatching null
                }
                val targetEdge = TARGET_STICKER_EDGE_PX
                var sampleSize = 1
                while (srcWidth / (sampleSize * 2) >= targetEdge &&
                    srcHeight / (sampleSize * 2) >= targetEdge
                ) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = sticker.emoji,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Sticker.canCommitInEditor(editorInstance: EditorInstance): Boolean {
    return commitMimeTypes.any { editorInstance.canCommitMimeType(it) }
}
