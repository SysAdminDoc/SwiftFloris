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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
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

// Shared LRU of decoded sticker bitmaps, keyed by sourceUri string.
//
// Before this cache: every scroll cycle re-entered each `StickerPreview`
// composable, which fired its `LaunchedEffect` again, which re-opened the
// SAF input stream and re-ran the two-pass decode. For a 240-sticker pack
// scrolled aggressively, the IME could allocate and release hundreds of
// bitmaps per minute.
//
// After this cache: the first composition for a sourceUri pays the SAF
// open + decode cost; every subsequent composition is a cache hit.
//
// Size budget: 64 entries × ≤512 px longest edge × 4 bytes/px ≈ 64 MB
// absolute worst case (all square 512×512), ≈ 13 MB at typical sizes.
// Downsampling keys on the longest edge, so non-square images are
// aggressively shrunk and never blow the per-tile budget.
private const val STICKER_BITMAP_CACHE_SIZE = 64
private val stickerBitmapCache: androidx.collection.LruCache<String, ImageBitmap> =
    androidx.collection.LruCache(STICKER_BITMAP_CACHE_SIZE)

fun evictStickerBitmapCache() {
    stickerBitmapCache.evictAll()
}

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
    var localStickerPack by remember { mutableStateOf<StickerPack?>(null) }
    var userStickerPack by remember { mutableStateOf<StickerPack?>(null) }
    var userStickerGrantLost by remember { mutableStateOf(false) }
    LaunchedEffect(context, userStickerFolderUri) {
        // Distinguish three states:
        //   - URI blank → no Imported tab.
        //   - URI set, grant valid, pack loadable → pack rendered.
        //   - URI set, grant lost → empty placeholder pack so the tab stays
        //     visible and the user gets a clear "open Settings to re-pick"
        //     message in the grid instead of a silently-vanishing tab.
        val (loadedLocalPack, loadedUserPack, grantLost) = withContext(Dispatchers.IO) {
            val grantValid = userStickerFolderUri.isNotBlank() &&
                UserStickerRepository.hasPersistableReadPermission(context, userStickerFolderUri)
            val importedPack = if (grantValid) {
                UserStickerRepository.loadPack(context, userStickerFolderUri)
            } else if (userStickerFolderUri.isNotBlank()) {
                StickerPack(
                    id = UserStickerRepository.PackId,
                    name = UserStickerRepository.PackName,
                    stickers = emptyList(),
                )
            } else {
                null
            }
            Triple(
                LocalStickerPackRepository.loadPack(context),
                importedPack,
                userStickerFolderUri.isNotBlank() && !grantValid,
            )
        }
        localStickerPack = loadedLocalPack
        userStickerGrantLost = grantLost
        userStickerPack = loadedUserPack
    }
    val packs = remember(localStickerPack, userStickerPack) {
        BundledStickerRepository.packs + listOfNotNull(localStickerPack, userStickerPack)
    }
    var activePackIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember(packs, searchQuery) {
        StickerSearch.search(packs, searchQuery)
    }
    val selectedPackIndex = activePackIndex.coerceIn(packs.indices)
    val activePack = packs[selectedPackIndex]
    val canInsertStickers = remember(activeEditorInfo.contentMimeTypes.toList(), packs) {
        packs.any { pack -> pack.stickers.any { sticker -> sticker.canCommitInEditor(editorInstance) } }
    }
    val isImportedTabActive = activePack.id == UserStickerRepository.PackId

    Column(modifier = modifier) {
        StickerSearchRow(
            query = searchQuery,
            onQueryChange = { query -> searchQuery = query },
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
        if (searchQuery.isNotBlank()) {
            val gridState = rememberLazyGridState()
            if (searchResults.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 12.dp),
                ) {
                    Text(text = stringRes(R.string.sticker__search__empty))
                }
            } else {
                StickerGrid(
                    stickers = searchResults,
                    gridState = gridState,
                    editorInstance = editorInstance,
                    onStickerTap = { sticker ->
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        commitSticker(editorInstance, sticker, scope, context)
                    },
                )
            }
            return@Column
        }
        StickerPackTabRow(
            packs = packs,
            selectedIndex = selectedPackIndex,
            onSelected = { index ->
                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                activePackIndex = index
            },
        )
        if (isImportedTabActive && userStickerGrantLost) {
            // Mirror of the v1.8.90 Settings-side preference-summary surface.
            // Android revoked the persistable SAF grant (e.g. the file
            // manager that issued the grant was uninstalled). The tab
            // stays visible so the user has a clear signal that the pack
            // is recoverable; tapping is currently not wired to the
            // Settings deep-link because the IME view runs in a different
            // process surface and can't open Settings activities cleanly,
            // so the message guides the user to do it manually.
            SnyggText(
                elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                text = stringRes(R.string.sticker__user_folder_grant_lost),
            )
        }
        val gridState = rememberLazyGridState()
        StickerGrid(
            stickers = activePack.stickers,
            gridState = gridState,
            editorInstance = editorInstance,
            onStickerTap = { sticker ->
                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                commitSticker(editorInstance, sticker, scope, context)
            },
        )
    }
}

@Composable
private fun StickerSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val style = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiTab.elementName)
    val searchContentDescription = stringRes(R.string.sticker__search__field_content_description)
    val clearSearchLabel = stringRes(R.string.sticker__search__clear)
    SnyggRow(
        elementName = FlorisImeUi.MediaEmojiTab.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(ButtonDefaults.IconSize),
            imageVector = Icons.Outlined.Search,
        )
        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = searchContentDescription
                },
            value = query,
            onValueChange = { value -> onQueryChange(value.take(40)) },
            singleLine = true,
            textStyle = TextStyle(
                color = style.foreground(),
                fontSize = 16.sp,
            ),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (query.isBlank()) {
                        Text(
                            text = stringRes(R.string.sticker__search__placeholder),
                            color = style.foreground().copy(alpha = 0.58f),
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotBlank()) {
            SnyggBox(
                elementName = FlorisImeUi.MediaEmojiTab.elementName,
                modifier = Modifier
                    .size(FlorisImeSizing.smartbarHeight)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = clearSearchLabel
                        onClick(label = clearSearchLabel) {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            onQueryChange("")
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            onQueryChange("")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                SnyggIcon(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    imageVector = Icons.Outlined.Close,
                )
            }
        }
    }
}

@Composable
private fun StickerGrid(
    stickers: List<Sticker>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    editorInstance: EditorInstance,
    onStickerTap: (Sticker) -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .florisScrollbar(gridState),
        columns = GridCells.Adaptive(minSize = StickerBaseWidth),
        state = gridState,
    ) {
        items(stickers, key = { sticker -> "${sticker.packId}:${sticker.id}" }) { sticker ->
            StickerTile(
                sticker = sticker,
                enabled = sticker.canCommitInEditor(editorInstance),
                onTap = { onStickerTap(sticker) },
            )
        }
    }
}

private fun commitSticker(
    editorInstance: EditorInstance,
    sticker: Sticker,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
) {
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
    val tileLabel = stringRes(
        if (enabled) R.string.sticker__tile_a11y else R.string.sticker__tile_disabled_a11y,
        "label" to sticker.label,
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(8.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(sticker.backgroundColor))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = tileLabel
                if (enabled) {
                    onClick(label = tileLabel) {
                        onTap()
                        true
                    }
                } else {
                    disabled()
                }
            }
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
    var bitmap by remember(sourceUri) {
        // Cache-warm initial value: if the bitmap was already decoded in a
        // prior composition (e.g. the user scrolled away and back), the
        // first frame after recomposition renders the cached bitmap with
        // no SAF open / decode round-trip. Cache-miss path still runs in
        // the LaunchedEffect below.
        mutableStateOf<ImageBitmap?>(stickerBitmapCache.get(sourceUri))
    }
    LaunchedEffect(sourceUri) {
        if (bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            stickerBitmapCache.get(sourceUri)?.let { return@withContext it }
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
                val longestEdge = maxOf(srcWidth, srcHeight)
                var sampleSize = 1
                while (longestEdge / (sampleSize * 2) >= TARGET_STICKER_EDGE_PX) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)?.asImageBitmap()
                }
            }.getOrNull()?.also { decoded ->
                stickerBitmapCache.put(sourceUri, decoded)
            }
        }
        bitmap = decoded
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
