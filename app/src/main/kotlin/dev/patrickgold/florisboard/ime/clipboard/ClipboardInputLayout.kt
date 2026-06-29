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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.media.KeyboardLikeButton
import dev.patrickgold.florisboard.ime.smartbar.AnimationDuration
import dev.patrickgold.florisboard.ime.smartbar.VerticalEnterTransition
import dev.patrickgold.florisboard.ime.smartbar.VerticalExitTransition
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.compose.DynamicFontScale
import dev.patrickgold.florisboard.lib.observeAsTransformingState
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.LocalLocalizedDateTimeFormatter
import org.florisboard.lib.compose.autoMirrorForRtl
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggQueryAttributes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

private val ItemWidth = 200.dp
private val DialogWidth = 240.dp
internal const val GRID_PREVIEW_CHAR_LIMIT = 500
internal const val POPUP_PREVIEW_CHAR_LIMIT = 2000
internal const val TEXT_A11Y_PREVIEW_CHAR_LIMIT = 140

internal fun capPreviewText(text: String, charLimit: Int): String {
    return if (text.length > charLimit) text.take(charLimit) + "…" else text
}

internal fun clipboardTextAccessibilityPreview(text: String): String {
    return capPreviewText(text.replace(Regex("\\s+"), " ").trim(), TEXT_A11Y_PREVIEW_CHAR_LIMIT)
}

const val CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO: Int = 0

@Composable
fun ClipboardInputLayout(
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val keyboardManager by context.keyboardManager()
    val androidKeyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    val deviceLocked = androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    val historyEnabled by prefs.clipboard.historyEnabled.collectAsState()
    val historySearchEnabled by prefs.clipboard.historySearchEnabled.collectAsState()

    var isFilterRowShown by remember { mutableStateOf(false) }
    val activeFilterTypes = remember { mutableStateSetOf<ItemType>() }
    val activeFilterTypeSnapshot = activeFilterTypes.toSet()
    var searchQuery by remember { mutableStateOf("") }
    val effectiveSearchQuery = if (historySearchEnabled) searchQuery else ""
    val hasActiveSearchQuery = effectiveSearchQuery.isNotBlank()
    val hasActiveFilters = activeFilterTypeSnapshot.isNotEmpty() || hasActiveSearchQuery

    val unfilteredHistory by clipboardManager.historyFlow.collectAsState()
    val hasClipboardHistory = unfilteredHistory.all.isNotEmpty()
    val filteredHistory = remember(unfilteredHistory, activeFilterTypeSnapshot, effectiveSearchQuery) {
        ClipboardHistoryFilter.filterByQueryAndType(
            history = unfilteredHistory,
            query = effectiveSearchQuery,
            activeTypes = activeFilterTypeSnapshot,
        )
    }

    val gridState = rememberLazyStaggeredGridState()
    var popupItem by remember(filteredHistory) { mutableStateOf<ClipboardItem?>(null) }
    var showClearAllHistory by remember { mutableStateOf(false) }

    fun isPopupSurfaceActive() = popupItem != null || showClearAllHistory

    LaunchedEffect(isFilterRowShown) {
        delay(AnimationDuration.toLong())
        if (!isFilterRowShown) {
            activeFilterTypes.clear()
        }
    }

    LaunchedEffect(historySearchEnabled) {
        if (!historySearchEnabled) {
            searchQuery = ""
        }
    }

    LaunchedEffect(activeFilterTypeSnapshot, effectiveSearchQuery) {
        gridState.scrollToItem(0)
    }

    @Composable
    fun HeaderRow() {
        SnyggRow(FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sizeModifier = Modifier
                .size(FlorisImeSizing.smartbarHeight)
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringRes(R.string.clip__back_to_text_input),
                )
            }
            SnyggText(
                elementName = FlorisImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.clipboard__header_title),
            )
            val historyToggleState = stringRes(
                if (historyEnabled) R.string.state__enabled else R.string.state__disabled,
            )
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { scope.launch { prefs.clipboard.historyEnabled.set(!historyEnabled) } },
                modifier = sizeModifier
                    .autoMirrorForRtl()
                    .semantics { stateDescription = historyToggleState },
                enabled = !deviceLocked && !isPopupSurfaceActive(),
            ) {
                SnyggIcon(
                    imageVector = if (historyEnabled) {
                        Icons.Default.ToggleOn
                    } else {
                        Icons.Default.ToggleOff
                    },
                    contentDescription = stringRes(R.string.clip__toggle_history),
                )
            }
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { showClearAllHistory = true },
                modifier = sizeModifier.autoMirrorForRtl(),
                enabled = !deviceLocked && historyEnabled && filteredHistory.all.isNotEmpty() && !isPopupSurfaceActive(),
            ) {
                SnyggIcon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringRes(R.string.clip__clear_history),
                )
            }
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { isFilterRowShown = !isFilterRowShown },
                modifier = sizeModifier,
                enabled = !deviceLocked && historyEnabled && hasClipboardHistory && !isPopupSurfaceActive(),
            ) {
                SnyggIcon(
                    imageVector = if (!isFilterRowShown) {
                        Icons.Default.FilterList
                    } else {
                        Icons.Default.FilterListOff
                    },
                    contentDescription = stringRes(
                        if (!isFilterRowShown) R.string.clip__show_filters else R.string.clip__hide_filters,
                    ),
                )
            }
            KeyboardLikeButton(
                modifier = sizeModifier,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.DELETE,
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                contentDescription = stringRes(R.string.key__backspace),
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Outlined.Backspace)
            }
        }
    }

    @Composable
    fun ClipboardSearchRow(
        query: String,
        onQueryChange: (String) -> Unit,
    ) {
        val style = rememberSnyggThemeQuery(FlorisImeUi.ClipboardSearchRow.elementName)
        val searchDescription = stringRes(R.string.clipboard__search__label)
        val searchFontSize = DynamicFontScale.fixedGeometrySp(16f, LocalDensity.current.fontScale)
        SnyggRow(
            elementName = FlorisImeUi.ClipboardSearchRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIcon(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .sizeIn(maxWidth = 24.dp, maxHeight = 24.dp),
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
            BasicTextField(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = searchDescription },
                value = query,
                onValueChange = { value -> onQueryChange(value.take(80)) },
                singleLine = true,
                textStyle = TextStyle(
                    color = style.foreground(),
                    fontSize = searchFontSize,
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text(
                                text = stringRes(R.string.clipboard__search__placeholder),
                                color = style.foreground().copy(alpha = 0.58f),
                                fontSize = searchFontSize,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotBlank()) {
                SnyggIconButton(
                    elementName = FlorisImeUi.ClipboardSearchButton.elementName,
                    modifier = Modifier.sizeIn(
                        minWidth = FlorisImeSizing.smartbarHeight,
                        minHeight = FlorisImeSizing.smartbarHeight,
                    ),
                    onClick = { onQueryChange("") },
                ) {
                    SnyggIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringRes(R.string.clipboard__search__clear),
                    )
                }
            }
        }
    }

    @Composable
    fun ClipItemView(
        elementName: String,
        item: ClipboardItem,
        contentScrollInsteadOfClip: Boolean,
        mediaGroup: ClipboardMediaItemGroup?,
        modifier: Modifier = Modifier,
    ) {
        val attributes = remember(item) {
            mapOf("type" to item.type.toString().lowercase())
        }
        val formatter = LocalLocalizedDateTimeFormatter.current
        val copiedTime = formatter.format(Instant.ofEpochMilli(item.creationTimestampMs))
        val mediaDescriptionKind = clipboardMediaDescriptionKind(item)
        val mediaA11yDescription = mediaDescriptionKind?.let { kind ->
            val mediaType = stringRes(kind.labelResId)
            mediaGroup?.let { group ->
                stringRes(
                    R.string.clipboard__media_item_a11y,
                    "media_type" to mediaType,
                    "group" to stringRes(group.labelResId),
                    "copied_time" to copiedTime,
                )
            } ?: stringRes(
                R.string.clipboard__media_item_a11y_no_group,
                "media_type" to mediaType,
                "copied_time" to copiedTime,
            )
        }
        val textA11yDescription = if (item.type == ItemType.TEXT) {
            val label = stringRes(
                if (item.isSensitive) {
                    R.string.clipboard__item_description_sensitive_text
                } else {
                    R.string.clipboard__item_description_text
                },
            )
            if (item.isSensitive) {
                mediaGroup?.let { group ->
                    stringRes(
                        R.string.clipboard__text_item_sensitive_a11y,
                        "text_type" to label,
                        "group" to stringRes(group.labelResId),
                        "copied_time" to copiedTime,
                    )
                } ?: stringRes(
                    R.string.clipboard__text_item_sensitive_a11y_no_group,
                    "text_type" to label,
                    "copied_time" to copiedTime,
                )
            } else {
                val previewText = clipboardTextAccessibilityPreview(item.stringRepresentation())
                mediaGroup?.let { group ->
                    stringRes(
                        R.string.clipboard__text_item_a11y,
                        "text_type" to label,
                        "group" to stringRes(group.labelResId),
                        "copied_time" to copiedTime,
                        "preview_text" to previewText,
                    )
                } ?: stringRes(
                    R.string.clipboard__text_item_a11y_no_group,
                    "text_type" to label,
                    "copied_time" to copiedTime,
                    "preview_text" to previewText,
                )
            }
        } else {
            null
        }
        val itemA11yDescription = mediaA11yDescription ?: textA11yDescription
        SnyggBox(
            elementName = elementName,
            attributes = attributes,
            modifier = modifier.fillMaxWidth(),
            clickAndSemanticsModifier = Modifier
                .run {
                    if (itemA11yDescription != null) {
                        semantics(mergeDescendants = true) { contentDescription = itemA11yDescription }
                    } else {
                        this
                    }
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    enabled = popupItem == null,
                    role = Role.Button,
                    onLongClickLabel = stringRes(R.string.clipboard__item_actions_a11y),
                    onLongClick = {
                        popupItem = item
                    },
                    onClick = {
                        clipboardManager.pasteItem(item)
                    },
                ),
        ) {
            if (item.type == ItemType.IMAGE) {
                val uri = item.uri
                if (uri == null) {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringRes(R.string.clipboard__media_image_unavailable),
                    )
                    return@SnyggBox
                }
                val id = ContentUris.parseId(uri)
                val file = ClipboardFileStorage.getFileForId(context, id)
                val bitmap = remember(id) {
                    runCatching {
                        check(file.exists()) { "Unable to resolve image at ${file.absolutePath}" }
                        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        checkNotNull(rawBitmap) { "Unable to decode image at ${file.absolutePath}" }
                        rawBitmap.asImageBitmap()
                    }
                }
                if (bitmap.isSuccess) {
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = bitmap.getOrThrow(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringRes(R.string.clipboard__media_load_error),
                    )
                }
            } else if (item.type == ItemType.VIDEO) {
                val uri = item.uri
                if (uri == null) {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringRes(R.string.clipboard__media_video_unavailable),
                    )
                    return@SnyggBox
                }
                val id = ContentUris.parseId(uri)
                val file = ClipboardFileStorage.getFileForId(context, id)
                val bitmap = remember(id) {
                    runCatching {
                        check(file.exists()) { "Unable to resolve video at ${file.absolutePath}" }
                        val rawBitmap = if (AndroidVersion.ATLEAST_API29_Q) {
                            val dataRetriever = MediaMetadataRetriever()
                            try {
                                dataRetriever.setDataSource(file.absolutePath)
                                val w = dataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                    ?.toIntOrNull() ?: 320
                                val h = dataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                    ?.toIntOrNull() ?: 240
                                ThumbnailUtils.createVideoThumbnail(file, Size(w, h), null)
                            } finally {
                                dataRetriever.release()
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                        }
                        checkNotNull(rawBitmap) { "Unable to decode video at ${file.absolutePath}" }
                        rawBitmap.asImageBitmap()
                    }
                }
                if (bitmap.isSuccess) {
                    Image(
                        modifier = Modifier.fillMaxWidth(),
                        bitmap = bitmap.getOrThrow(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                    )
                    Icon(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, bottom = 4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                    )
                } else {
                    SnyggText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringRes(R.string.clipboard__media_load_error),
                    )
                }
            } else {
                val charLimit = if (contentScrollInsteadOfClip) POPUP_PREVIEW_CHAR_LIMIT else GRID_PREVIEW_CHAR_LIMIT
                val previewText = capPreviewText(item.displayText(), charLimit)
                Column {
                    ClipTextItemDescription(
                        elementName = FlorisImeUi.ClipboardItemDescription.elementName,
                        attributes = attributes,
                        item = item,
                    )
                    SnyggText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .run { if (contentScrollInsteadOfClip) this.florisVerticalScroll() else this },
                        text = previewText,
                    )
                }
            }
        }
    }

    @Composable
    fun HistoryMainView() {
        SnyggBox(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            val historyAlpha by animateFloatAsState(targetValue = if (isPopupSurfaceActive()) 0.12f else 1f)
            val staggeredGridCells by prefs.clipboard.historyNumGridColumns()
                .observeAsTransformingState { numGridColumns ->
                    if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                        StaggeredGridCells.Adaptive(160.dp)
                    } else {
                        StaggeredGridCells.Fixed(numGridColumns)
                    }
                }

            fun LazyStaggeredGridScope.clipboardItems(
                items: List<ClipboardItem>,
                key: String,
                @StringRes title: Int,
                mediaGroup: ClipboardMediaItemGroup,
            ) {
                if (items.isNotEmpty()) {
                    item(key, span = StaggeredGridItemSpan.FullLine) {
                        ClipCategoryTitle(text = stringRes(title))
                    }
                    items(items) { item ->
                        ClipItemView(
                            elementName = FlorisImeUi.ClipboardItem.elementName,
                            item = item,
                            contentScrollInsteadOfClip = false,
                            mediaGroup = mediaGroup,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(historyAlpha),
            ) {
                AnimatedVisibility(
                    visible = historySearchEnabled && (hasClipboardHistory || searchQuery.isNotBlank()),
                    enter = VerticalEnterTransition,
                    exit = VerticalExitTransition,
                ) {
                    ClipboardSearchRow(
                        query = searchQuery,
                        onQueryChange = { query -> searchQuery = query },
                    )
                }
                AnimatedVisibility(
                    visible = isFilterRowShown,
                    enter = VerticalEnterTransition,
                    exit = VerticalExitTransition,
                ) {
                    SnyggRow(
                        elementName = FlorisImeUi.ClipboardFilterRow.elementName,
                        modifier = Modifier.fillMaxWidth(),
                        clickAndSemanticsModifier = Modifier.florisHorizontalScroll(),
                    ) {
                        @Composable
                        fun FilterChip(
                            imageVector: ImageVector,
                            text: String,
                            itemType: ItemType,
                        ) {
                            val active = activeFilterTypes.contains(itemType)
                            val attributes = remember(active) {
                                mapOf(
                                    "state" to if (active) "active" else "inactive",
                                    "type" to itemType.toString().lowercase(),
                                )
                            }
                            SnyggChip(
                                elementName = FlorisImeUi.ClipboardFilterChip.elementName,
                                attributes = attributes,
                                onClick = {
                                    if (!activeFilterTypes.add(itemType)) {
                                        activeFilterTypes.remove(itemType)
                                    }
                                },
                                imageVector = imageVector,
                                text = text,
                            )
                        }

                        FilterChip(
                            imageVector = Icons.Default.TextFields,
                            text = stringRes(R.string.clipboard__filter_chip__text),
                            itemType = ItemType.TEXT,
                        )
                        FilterChip(
                            imageVector = Icons.Default.Image,
                            text = stringRes(R.string.clipboard__filter_chip__images),
                            itemType = ItemType.IMAGE,
                        )
                        FilterChip(
                            imageVector = Icons.Default.Movie,
                            text = stringRes(R.string.clipboard__filter_chip__videos),
                            itemType = ItemType.VIDEO,
                        )
                    }
                }
                SnyggBox(FlorisImeUi.ClipboardGrid.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        columns = staggeredGridCells,
                    ) {
                        if (filteredHistory.all.isEmpty()) {
                            item("filtered-empty", span = StaggeredGridItemSpan.FullLine) {
                                val title = when {
                                    hasActiveSearchQuery -> R.string.clipboard__search_empty__title
                                    else -> R.string.clipboard__filter_empty__title
                                }
                                val message = when {
                                    hasActiveSearchQuery && activeFilterTypeSnapshot.isNotEmpty() ->
                                        R.string.clipboard__search_filter_empty__message
                                    hasActiveSearchQuery -> R.string.clipboard__search_empty__message
                                    else -> R.string.clipboard__filter_empty__message
                                }
                                ClipboardEmptyMessage(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    title = stringRes(title),
                                    message = stringRes(message),
                                )
                            }
                        }
                        clipboardItems(
                            items = filteredHistory.pinned,
                            key = "pinned-header",
                            title = R.string.clipboard__group_pinned,
                            mediaGroup = ClipboardMediaItemGroup.PINNED,
                        )
                        clipboardItems(
                            items = filteredHistory.recent,
                            key = "recent-header",
                            title = R.string.clipboard__group_recent,
                            mediaGroup = ClipboardMediaItemGroup.RECENT,
                        )
                        clipboardItems(
                            items = filteredHistory.other,
                            key = "other-header",
                            title = R.string.clipboard__group_other,
                            mediaGroup = ClipboardMediaItemGroup.OTHER,
                        )
                    }
                }
            }

            val activePopupItem = popupItem
            if (activePopupItem != null) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { popupItem = null }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        ClipItemView(
                            elementName = FlorisImeUi.ClipboardItemPopup.elementName,
                            modifier = Modifier
                                .widthIn(max = ItemWidth)
                                .weight(1f, fill = false),
                            item = activePopupItem,
                            contentScrollInsteadOfClip = true,
                            mediaGroup = if (activePopupItem.isPinned) {
                                ClipboardMediaItemGroup.PINNED
                            } else {
                                null
                            },
                        )
                        SnyggBox(FlorisImeUi.ClipboardItemTimestamp.elementName) {
                            val formatter = LocalLocalizedDateTimeFormatter.current
                            SnyggText(
                                modifier = Modifier.fillMaxWidth(),
                                text = formatter.format(Instant.ofEpochMilli(activePopupItem.creationTimestampMs)),
                            )
                        }
                    }
                    SnyggColumn(modifier = Modifier.weight(0.5f)) {
                        SnyggColumn(FlorisImeUi.ClipboardItemActions.elementName) {
                            PopupAction(
                                icon = Icons.Outlined.PushPin,
                                text = stringRes(if (activePopupItem.isPinned) {
                                    R.string.clip__unpin_item
                                } else {
                                    R.string.clip__pin_item
                                }),
                            ) {
                                if (activePopupItem.isPinned) {
                                    clipboardManager.unpinClip(activePopupItem)
                                } else {
                                    clipboardManager.pinClip(activePopupItem)
                                }
                                popupItem = null
                            }
                            PopupAction(
                                icon = Icons.Default.Delete,
                                text = stringRes(R.string.clip__delete_item),
                            ) {
                                clipboardManager.deleteClip(activePopupItem, onlyIfUnpinned = false)
                                popupItem = null
                            }
                            PopupAction(
                                icon = Icons.Outlined.ContentPasteGo,
                                text = stringRes(R.string.clip__paste_item),
                            ) {
                                clipboardManager.pasteItem(activePopupItem)
                                popupItem = null
                            }
                        }
                    }
                }
            }

            if (showClearAllHistory) {
                SnyggRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { showClearAllHistory = false }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    SnyggColumn(
                        elementName = FlorisImeUi.ClipboardClearAllDialog.elementName,
                        modifier = Modifier
                            .width(DialogWidth)
                            .pointerInput(Unit) {
                                detectTapGestures { /* Do nothing */ }
                            },
                    ) {
                        SnyggText(
                            elementName = FlorisImeUi.ClipboardClearAllDialogMessage.elementName,
                            text = stringRes(
                                if (hasActiveFilters) {
                                    R.string.clipboard__confirm_clear_filtered_history__message
                                } else {
                                    R.string.clipboard__confirm_clear_unfiltered_history__message
                                }
                            ),
                        )
                        SnyggRow(FlorisImeUi.ClipboardClearAllDialogButtons.elementName) {
                            Spacer(modifier = Modifier.weight(1f))
                            SnyggButton(
                                elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "no"),
                                onClick = {
                                    showClearAllHistory = false
                                },
                            ) {
                                SnyggText(
                                    text = stringRes(R.string.action__no),
                                )
                            }
                            SnyggButton(
                                elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                                attributes = mapOf("action" to "yes"),
                                onClick = {
                                    clipboardManager.clearExactHistory(filteredHistory.unpinned)
                                    scope.launch {
                                        context.showShortToast(R.string.clipboard__cleared_history)
                                    }
                                    showClearAllHistory = false
                                },
                            ) {
                                SnyggText(
                                    text = stringRes(R.string.action__yes),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryEmptyView() {
        ClipboardEmptyMessage(
            title = stringRes(R.string.clipboard__empty__title),
            message = stringRes(R.string.clipboard__empty__message),
        )
    }

    @Composable
    fun HistoryDisabledView() {
        SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryDisabledTitle.elementName,
                modifier = Modifier.padding(bottom = 8.dp),
                text = stringRes(R.string.clipboard__disabled__title),
            )
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryDisabledMessage.elementName,
                text = stringRes(R.string.clipboard__disabled__message),
            )
            SnyggButton(FlorisImeUi.ClipboardHistoryDisabledButton.elementName,
                onClick = { scope.launch { prefs.clipboard.historyEnabled.set(true) } },
                modifier = Modifier.align(Alignment.End),
            ) {
                SnyggText(
                    text = stringRes(R.string.clipboard__disabled__enable_button),
                )
            }
        }
    }

    @Composable
    fun HistoryLockedView() {
        SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryLockedTitle.elementName,
                text = stringRes(R.string.clipboard__locked__title),
            )
            SnyggText(
                elementName = FlorisImeUi.ClipboardHistoryLockedMessage.elementName,
                text = stringRes(R.string.clipboard__locked__message),
            )
        }
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        HeaderRow()
        if (deviceLocked) {
            HistoryLockedView()
        } else {
            if (historyEnabled) {
                if (hasClipboardHistory || hasActiveFilters) {
                    HistoryMainView()
                } else {
                    HistoryEmptyView()
                }
            } else {
                HistoryDisabledView()
            }
        }
    }
}

@Composable
private fun ClipboardEmptyMessage(
    modifier: Modifier = Modifier.fillMaxSize(),
    title: String,
    message: String,
) {
    SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SnyggText(
            text = title,
        )
        SnyggText(
            text = message,
        )
    }
}

@Composable
private fun ClipCategoryTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    SnyggText(FlorisImeUi.ClipboardSubheader.elementName,
        modifier = modifier.fillMaxWidth(),
        text = text.uppercase(),
    )
}

@Composable
private fun ClipTextItemDescription(
    elementName: String,
    attributes: SnyggQueryAttributes,
    item: ClipboardItem,
    modifier: Modifier = Modifier,
): Unit = with(LocalDensity.current) {
    val (icon, description) = when (clipboardItemDescriptionKind(item)) {
        ClipboardItemDescriptionKind.EMAIL -> {
            Icons.Outlined.Email to stringRes(R.string.clipboard__item_description_email)
        }
        ClipboardItemDescriptionKind.URL -> {
            Icons.Default.Link to stringRes(R.string.clipboard__item_description_url)
        }
        ClipboardItemDescriptionKind.PHONE -> {
            Icons.Default.Phone to stringRes(R.string.clipboard__item_description_phone)
        }
        null -> null to null
    }
    if (icon != null && description != null) {
        SnyggRow(
            elementName = elementName,
            attributes = attributes,
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIcon(
                imageVector = icon,
            )
            SnyggText(
                modifier = Modifier.weight(1f),
                text = description,
            )
        }
    }
}

internal enum class ClipboardItemDescriptionKind {
    EMAIL,
    URL,
    PHONE,
}

internal enum class ClipboardMediaDescriptionKind(@param:StringRes val labelResId: Int) {
    IMAGE(R.string.clipboard__item_description_image),
    VIDEO(R.string.clipboard__item_description_video),
}

internal enum class ClipboardMediaItemGroup(@param:StringRes val labelResId: Int) {
    PINNED(R.string.clipboard__group_pinned),
    RECENT(R.string.clipboard__group_recent),
    OTHER(R.string.clipboard__group_other),
}

internal fun clipboardItemDescriptionKind(item: ClipboardItem): ClipboardItemDescriptionKind? {
    if (item.type != ItemType.TEXT || item.isSensitive) {
        return null
    }
    val text = item.stringRepresentation()
    return when {
        NetworkUtils.isEmailAddress(text) -> ClipboardItemDescriptionKind.EMAIL
        NetworkUtils.isUrl(text) -> ClipboardItemDescriptionKind.URL
        NetworkUtils.isPhoneNumber(text) -> ClipboardItemDescriptionKind.PHONE
        else -> null
    }
}

internal fun clipboardMediaDescriptionKind(item: ClipboardItem): ClipboardMediaDescriptionKind? {
    return when (item.type) {
        ItemType.IMAGE -> ClipboardMediaDescriptionKind.IMAGE
        ItemType.VIDEO -> ClipboardMediaDescriptionKind.VIDEO
        ItemType.TEXT -> null
    }
}

@Composable
private fun PopupAction(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SnyggRow(FlorisImeUi.ClipboardItemAction.elementName,
        modifier = modifier.rippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(FlorisImeUi.ClipboardItemActionIcon.elementName,
            imageVector = icon,
        )
        SnyggText(FlorisImeUi.ClipboardItemActionText.elementName,
            modifier = Modifier.weight(1f),
            text = text,
        )
    }
}
