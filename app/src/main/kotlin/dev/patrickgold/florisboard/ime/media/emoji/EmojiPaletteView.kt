/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.widget.EmojiTextView
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.header
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.ceil

private val EmojiCategoryValues = EmojiCategory.entries
private val EmojiBaseWidth = 42.dp
private val EmojiDefaultFontSize = 22.sp

private val VariantsTriangleShapeLtr = GenericShape { size, _ ->
    moveTo(x = size.width, y = 0f)
    lineTo(x = size.width, y = size.height)
    lineTo(x = 0f, y = size.height)
}

private val VariantsTriangleShapeRtl = GenericShape { size, _ ->
    moveTo(x = 0f, y = 0f)
    lineTo(x = size.width, y = size.height)
    lineTo(x = 0f, y = size.height)
}

data class EmojiMappingForView(
    val pinned: List<EmojiSet>,
    val recent: List<EmojiSet>,
    val simple: List<EmojiSet>,
)

@Composable
fun EmojiPaletteView(
    fullEmojiMappings: EmojiData,
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val editorInstance by context.editorInstance()
    val keyboardManager by context.keyboardManager()

    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
    val systemFontPaint = remember(Typeface.DEFAULT) {
        Paint().apply {
            typeface = Typeface.DEFAULT
        }
    }
    val metadataVersion = activeEditorInfo.emojiCompatMetadataVersion
    val replaceAll = activeEditorInfo.emojiCompatReplaceAll
    val emojiCompatInstance by FlorisEmojiCompat.getAsFlow(replaceAll).collectAsState()
    val emojiMappings = remember(emojiCompatInstance, fullEmojiMappings, metadataVersion, systemFontPaint) {
        fullEmojiMappings.byCategory.mapValues { (_, emojiSetList) ->
            emojiSetList.mapNotNull { emojiSet ->
                emojiSet.emojis.filter { emoji ->
                    // ROADMAP §6 N17.1 — defensive guard against empty
                    // emoji.value. `Paint.hasGlyph("")` throws
                    // IllegalArgumentException ("hasGlyph called with empty
                    // string"); EmojiCompat.getEmojiMatch on an empty
                    // CharSequence is also documented to throw. Empty
                    // values can leak in through (a) malformed history
                    // JSON via Emoji.ValueOnlySerializer (a `""` entry
                    // round-trips silently), (b) an asset-data line that
                    // somehow shipped without a base codepoint. We filter
                    // those out before they hit either downstream
                    // function so the palette never crashes on render.
                    emoji.value.isNotEmpty() && (
                        emojiCompatInstance?.getEmojiMatch(emoji.value, metadataVersion) == EmojiCompat.EMOJI_SUPPORTED ||
                            systemFontPaint.hasGlyph(emoji.value)
                    )
                }.let { if (it.isEmpty()) null else EmojiSet(it) }
            }
        }
    }
    val androidKeyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    val deviceLocked = androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }

    val preferredSkinTone by prefs.emoji.preferredSkinTone.collectAsState()
    val emojiHistoryEnabled by prefs.emoji.historyEnabled.collectAsState()

    var activeCategory by remember(emojiHistoryEnabled) {
        if (emojiHistoryEnabled) {
            mutableStateOf(EmojiCategory.RECENTLY_USED)
        } else {
            mutableStateOf(EmojiCategory.SMILEYS_EMOTION)
        }
    }
    var recentlyUsedVersion by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember(emojiMappings, searchQuery) {
        EmojiSearch.results(emojiMappings, searchQuery)
    }
    val scope = rememberCoroutineScope()

    // ROADMAP §7 Next-9.4a — pinned-emoji-groups palette row state. Tracks
    // version counters so the chip strip and in-keyboard pin sheet rebuild
    // when learn/forget calls mutate the underlying EmojiPinGroupStore.
    val emojiPinGroupStore = remember(context) { EmojiPinGroupStore.get(context) }
    val pinToGroupSheetState = remember(emojiPinGroupStore) {
        PinToGroupSheetState.forStore(emojiPinGroupStore)
    }
    var pinnedGroupsVersion by remember { mutableIntStateOf(0) }
    var pinSheetVersion by remember { mutableIntStateOf(0) }
    val pinnedGroupChips = remember(pinnedGroupsVersion) {
        PinnedGroupChip.fromStoreSnapshot(
            emojiPinGroupStore.snapshot(),
        )
    }

    fun openPinToGroupSheet(emoji: Emoji) {
        pinToGroupSheetState.open(emoji.value)
        pinSheetVersion++
    }

    @Composable
    fun GridHeader(text: String) {
        SnyggText(
            elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
            text = text,
        )
    }

    @Composable
    fun EmojiKeyWrapper(
        emojiSet: EmojiSet,
        isPinned: Boolean = false,
        isRecent: Boolean = false,
    ) {
        EmojiKey(
            emojiSet = emojiSet,
            emojiCompatInstance = emojiCompatInstance,
            preferredSkinTone = preferredSkinTone,
            isPinned = isPinned,
            isRecent = isRecent,
            onEmojiInput = { emoji ->
                keyboardManager.inputEventDispatcher.sendDownUp(emoji)
                scope.launch {
                    EmojiHistoryHelper.markEmojiUsed(prefs, emoji)
                }
            },
            onHistoryAction = {
                recentlyUsedVersion++
            },
            onPinToGroup = ::openPinToGroupSheet,
        )
    }

    fun calculatePageNumbers(): Int {
        return when {
            !emojiHistoryEnabled -> EmojiCategoryValues.size - 1
            else -> EmojiCategoryValues.size
        }
    }

    fun pageNumberToCategory(pageNumber: Int): EmojiCategory {
        return when {
            !emojiHistoryEnabled -> EmojiCategoryValues[pageNumber + 1]
            else -> EmojiCategoryValues[pageNumber]
        }
    }

    fun categoryToPageNumber(category: EmojiCategory): Int {
        return if (emojiHistoryEnabled) {
            EmojiCategoryValues.indexOf(category)
        } else {
            EmojiCategoryValues.indexOf(category) - 1
        }
    }


    @Composable
    fun EmojiSearchRow(
        query: String,
        onQueryChange: (String) -> Unit,
    ) {
        val inputFeedbackController = LocalInputFeedbackController.current
        val style = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiTab.elementName)
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
                modifier = Modifier.weight(1f),
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
                                text = stringRes(R.string.emoji__search__placeholder),
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
    fun EmojiCategoriesTabRow(
        activeCategory: EmojiCategory,
        onCategoryChange: (EmojiCategory) -> Unit,
    ) {
        val inputFeedbackController = LocalInputFeedbackController.current
        val selectedTabIndex = categoryToPageNumber(activeCategory)
        val style = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiTab.elementName)
        PrimaryTabRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = style.foreground(),
            indicator = {
                val style = rememberSnyggThemeQuery(
                    elementName = FlorisImeUi.MediaEmojiTab.elementName,
                    selector = SnyggSelector.FOCUS,
                )
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(selectedTabIndex),
                    height = 4.dp,
                    color = style.foreground(),
                )
            },
        ) {
            for (category in EmojiCategoryValues) {
                if (category == EmojiCategory.RECENTLY_USED && !emojiHistoryEnabled) {
                    continue
                }
                Tab(
                    onClick = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        onCategoryChange(category)
                    },
                    selected = activeCategory == category,
                    icon = { SnyggIcon(
                        elementName = FlorisImeUi.MediaEmojiTab.elementName,
                        selector = if (activeCategory == category) SnyggSelector.FOCUS else SnyggSelector.NONE,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        imageVector = category.icon(),
                    ) },
                )
            }
        }
    }

    Column(
        modifier = modifier
    ) {
        EmojiSearchRow(
            query = searchQuery,
            onQueryChange = { query -> searchQuery = query },
        )
        if (searchQuery.isNotBlank()) {
            val lazyGridState = rememberLazyGridState()
            if (searchResults.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 12.dp),
                ) {
                    Text(text = stringRes(R.string.emoji__search__empty))
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .florisScrollbar(lazyGridState),
                    columns = GridCells.Adaptive(minSize = EmojiBaseWidth),
                    state = lazyGridState,
                ) {
                    items(searchResults, key = { emojiSet -> emojiSet.base().value }) { emojiSet ->
                        EmojiKeyWrapper(emojiSet)
                    }
                }
            }
        } else {
            val pagerState = rememberPagerState(
                pageCount = { calculatePageNumbers() }
            )

            // Reset the pager to the first page when emojiHistory is enabled
            LaunchedEffect(emojiHistoryEnabled) {
                pagerState.animateScrollToPage(0)
            }

            EmojiCategoriesTabRow(
                activeCategory = activeCategory,
                onCategoryChange = { category ->
                    activeCategory = category
                    scope.launch { pagerState.animateScrollToPage(categoryToPageNumber(activeCategory)) }
                },
            )
            HorizontalPager(pagerState, beyondViewportPageCount = 1) { page ->
                // Every page needs its own lazyGridState in order to scroll correctly
                val lazyGridState = rememberLazyGridState()

                // Update the lazyGridState and active category on scroll
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        lazyGridState.scrollToItem(0)
                        activeCategory = pageNumberToCategory(page)
                        recentlyUsedVersion++
                    }
                }

                val category = pageNumberToCategory(page)
                val emojiMapping = if (category == EmojiCategory.RECENTLY_USED) {
                    // Purposely using remember here to prevent recomposition, as this would cause rapid
                    // emoji changes for the user when in recently used category.
                    remember(recentlyUsedVersion) {
                        val data = prefs.emoji.historyData.get()
                        // ROADMAP §6 N17.1 — drop history entries with an
                        // empty `value`. They can leak in through Emoji
                        // .ValueOnlySerializer when a corrupt persisted
                        // history JSON round-trips a `""` entry. The
                        // palette grid would otherwise render invisible
                        // tap targets and the long-press popup would
                        // commit an empty string into the editor.
                        EmojiMappingForView(
                            pinned = data.pinned.filter { it.value.isNotEmpty() }
                                .map { EmojiSet(listOf(it)) },
                            recent = data.recent.filter { it.value.isNotEmpty() }
                                .map { EmojiSet(listOf(it)) },
                            simple = emptyList(),
                        )
                    }
                } else {
                    EmojiMappingForView(
                        pinned = emptyList(),
                        recent = emptyList(),
                        simple = emojiMappings[category]!!,
                    )
                }

                val isEmojiHistoryEmpty = emojiMapping.pinned.isEmpty() && emojiMapping.recent.isEmpty()
                when (category) {
                    EmojiCategory.RECENTLY_USED if deviceLocked -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(all = 8.dp),
                        ) {
                            Text(
                                text = stringRes(R.string.emoji__history__phone_locked_message),
                            )
                        }
                    }
                    EmojiCategory.RECENTLY_USED if isEmojiHistoryEmpty -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(all = 8.dp),
                        ) {
                            Text(
                                text = stringRes(R.string.emoji__history__empty_message),
                            )
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                text = stringRes(R.string.emoji__history__usage_tip),
                                fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                    else -> key(emojiMapping) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // ROADMAP §7 Next-9.4a — pinned-emoji-groups chip row
                            // (only on the RECENTLY_USED tab; other tabs would clutter).
                            if (category == EmojiCategory.RECENTLY_USED && pinnedGroupChips.isNotEmpty()) {
                                PinnedGroupsPaletteRow(
                                    groups = pinnedGroupChips,
                                    onGroupTapped = { groupName ->
                                        val emojis = emojiPinGroupStore.emojisFor(groupName)
                                        for (emojiText in emojis) {
                                            keyboardManager.inputEventDispatcher.sendDownUp(
                                                Emoji(value = emojiText, name = groupName, keywords = emptyList()),
                                            )
                                        }
                                        scope.launch {
                                            for (emojiText in emojis) {
                                                EmojiHistoryHelper.markEmojiUsed(
                                                    prefs = prefs,
                                                    emoji = Emoji(value = emojiText, name = groupName, keywords = emptyList()),
                                                )
                                            }
                                            recentlyUsedVersion++
                                        }
                                    },
                                    onGroupLongPressed = { groupName ->
                                        scope.launch {
                                            context.showShortToast(
                                                R.string.emoji__pin_group__chip_hint,
                                                "group" to groupName,
                                            )
                                        }
                                    },
                                )
                            }
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .florisScrollbar(lazyGridState),
                                columns = GridCells.Adaptive(minSize = EmojiBaseWidth),
                                state = lazyGridState,
                            ) {
                            if (emojiMapping.pinned.isNotEmpty()) {
                                header("header_pinned") {
                                    GridHeader(text = stringRes(R.string.emoji__history__pinned))
                                }
                                items(emojiMapping.pinned) { emojiSet ->
                                    EmojiKeyWrapper(emojiSet, isPinned = true)
                                }
                            }
                            if (emojiMapping.recent.isNotEmpty()) {
                                header("header_recent") {
                                    GridHeader(text = stringRes(R.string.emoji__history__recent))
                                }
                                items(emojiMapping.recent) { emojiSet ->
                                    EmojiKeyWrapper(emojiSet, isRecent = true)
                                }
                            }
                            if (emojiMapping.simple.isNotEmpty()) {
                                items(emojiMapping.simple) { emojiSet ->
                                    EmojiKeyWrapper(emojiSet)
                                }
                            }
                            }  // close LazyVerticalGrid
                        }  // close Column
                    }
                }
            }
        }
        PinToGroupSheet(
            state = pinToGroupSheetState,
            version = pinSheetVersion,
            onStateChanged = {
                pinSheetVersion++
            },
            onPinned = { groupName ->
                pinnedGroupsVersion++
                pinSheetVersion++
                scope.launch {
                    context.showShortToast(
                        R.string.emoji__pin_group__pinned_toast,
                        "group" to groupName,
                    )
                }
            },
        )
    }
}

@Composable
private fun EmojiKey(
    emojiSet: EmojiSet,
    emojiCompatInstance: EmojiCompat?,
    preferredSkinTone: EmojiSkinTone,
    isPinned: Boolean,
    isRecent: Boolean,
    onEmojiInput: (Emoji) -> Unit,
    onHistoryAction: () -> Unit,
    onPinToGroup: (Emoji) -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val base = emojiSet.base(withSkinTone = preferredSkinTone)
    val variations = emojiSet.variations(withoutSkinTone = preferredSkinTone)
    var showVariantsBox by remember { mutableStateOf(false) }

    SnyggBox(FlorisImeUi.MediaEmojiKey.elementName,
        modifier = Modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    },
                    onTap = {
                        onEmojiInput(base)
                    },
                    onLongPress = {
                        inputFeedbackController.keyLongPress(TextKeyData.UNSPECIFIED)
                        if (variations.isNotEmpty() || isPinned || isRecent) {
                            showVariantsBox = true
                        } else {
                            onPinToGroup(base)
                        }
                    },
                )
            },
    ) {
        EmojiText(
            modifier = Modifier.align(Alignment.Center),
            text = base.value,
            emojiCompatInstance = emojiCompatInstance,
        )
        if (variations.isNotEmpty() || isPinned || isRecent) {
            val style = rememberSnyggThemeQuery(FlorisImeUi.MediaEmojiKeyPopupExtendedIndicator.elementName)
            val shape = when (LocalLayoutDirection.current) {
                LayoutDirection.Ltr -> VariantsTriangleShapeLtr
                LayoutDirection.Rtl -> VariantsTriangleShapeRtl
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(4.dp)
                    .background(style.foreground(), shape),
            )
        }

        if (isPinned || isRecent) {
            EmojiHistoryPopup(
                emoji = base,
                visible = showVariantsBox,
                isCurrentlyPinned = isPinned,
                onHistoryAction = {
                    onHistoryAction()
                    showVariantsBox = false
                },
                onPinToGroup = {
                    onPinToGroup(base)
                    showVariantsBox = false
                },
                onDismiss = {
                    showVariantsBox = false
                },
            )
        } else {
            EmojiVariationsPopup(
                variations = variations,
                visible = showVariantsBox,
                emojiCompatInstance = emojiCompatInstance,
                onEmojiTap = { emoji ->
                    onEmojiInput(emoji)
                    showVariantsBox = false
                },
                onPinBaseToGroup = {
                    onPinToGroup(base)
                    showVariantsBox = false
                },
                onDismiss = {
                    showVariantsBox = false
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiVariationsPopup(
    variations: List<Emoji>,
    visible: Boolean,
    emojiCompatInstance: EmojiCompat?,
    onEmojiTap: (Emoji) -> Unit,
    onPinBaseToGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val emojiKeyHeight = FlorisImeSizing.smartbarHeight

    if (visible) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = with(LocalDensity.current) {
                val y = -emojiKeyHeight * ceil((variations.size + 1) / 6f)
                IntOffset(x = 0, y = y.toPx().toInt())
            },
            onDismissRequest = onDismiss,
        ) {
            SnyggRow(
                elementName = FlorisImeUi.MediaEmojiKeyPopupBox.elementName,
                modifier = Modifier
                    .widthIn(max = EmojiBaseWidth * 6),
            ) {
                SnyggBox(
                    elementName = FlorisImeUi.MediaEmojiKeyPopupElement.elementName,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { onPinBaseToGroup() }
                        }
                        .width(EmojiBaseWidth)
                        .height(emojiKeyHeight),
                ) {
                    SnyggIcon(
                        modifier = Modifier.align(Alignment.Center),
                        imageVector = Icons.Outlined.Add,
                    )
                }
                for (emoji in variations) {
                    SnyggBox(
                        elementName = FlorisImeUi.MediaEmojiKeyPopupElement.elementName,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { onEmojiTap(emoji) }
                            }
                            .width(EmojiBaseWidth)
                            .height(emojiKeyHeight),
                    ) {
                        EmojiText(
                            modifier = Modifier.align(Alignment.Center),
                            text = emoji.value,
                            emojiCompatInstance = emojiCompatInstance,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiHistoryPopup(
    emoji: Emoji,
    visible: Boolean,
    isCurrentlyPinned: Boolean,
    onHistoryAction: () -> Unit,
    onPinToGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val emojiKeyHeight = FlorisImeSizing.smartbarHeight
    val context = LocalContext.current
    val pinnedUS by prefs.emoji.historyPinnedUpdateStrategy.collectAsState()
    val recentUS by prefs.emoji.historyRecentUpdateStrategy.collectAsState()
    val showMoveLeft = isCurrentlyPinned && !pinnedUS.isAutomatic || !recentUS.isAutomatic
    val showMoveRight = isCurrentlyPinned && !pinnedUS.isAutomatic || !recentUS.isAutomatic

    @Composable
    fun Action(icon: ImageVector, action: suspend () -> Unit) {
        SnyggBox(
            elementName = FlorisImeUi.MediaEmojiKeyPopupElement.elementName,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures {
                        scope.launch {
                            action()
                            onHistoryAction()
                        }
                    }
                }
                .width(EmojiBaseWidth)
                .height(emojiKeyHeight),
        ) {
            SnyggIcon(
                modifier = Modifier.align(Alignment.Center),
                imageVector = icon,
            )
        }
    }

    val numActions = 3 + (if (showMoveLeft) 1 else 0) + (if (showMoveRight) 1 else 0)
    if (visible) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = with(LocalDensity.current) {
                val y = -emojiKeyHeight * ceil(numActions / 6f)
                IntOffset(x = 0, y = y.toPx().toInt())
            },
            onDismissRequest = onDismiss,
        ) {
            SnyggRow(
                elementName = FlorisImeUi.MediaEmojiKeyPopupBox.elementName,
                modifier = Modifier
                    .widthIn(max = EmojiBaseWidth * 6),
            ) {
                if (isCurrentlyPinned) {
                    Action(
                        icon = Icons.Outlined.PushPin,
                        action = {
                            EmojiHistoryHelper.unpinEmoji(prefs, emoji)
                        },
                    )
                } else {
                    Action(
                        icon = Icons.Outlined.PushPin,
                        action = {
                            EmojiHistoryHelper.pinEmoji(prefs, emoji)
                        },
                    )
                }
                if (showMoveLeft) {
                    Action(
                        icon = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                        action = {
                            EmojiHistoryHelper.moveEmoji(prefs, emoji, -1)
                        },
                    )
                }
                if (showMoveRight) {
                    Action(
                        icon = Icons.AutoMirrored.Default.KeyboardArrowRight,
                        action = {
                            EmojiHistoryHelper.moveEmoji(prefs, emoji, 1)
                        },
                    )
                }
                Action(
                    icon = Icons.Outlined.Add,
                    action = {
                        onPinToGroup()
                    },
                )
                Action(
                    icon = Icons.Outlined.Delete,
                    action = {
                        EmojiHistoryHelper.removeEmoji(prefs, emoji)
                        context.showShortToast(
                            R.string.emoji__history__removal_success_message,
                            "emoji" to emoji.value,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun EmojiText(
    text: String,
    emojiCompatInstance: EmojiCompat?,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    fontSize: TextUnit = EmojiDefaultFontSize,
) {
    if (emojiCompatInstance != null) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                EmojiTextView(context).also {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
                    it.setTextColor(color.toArgb())
                }
            },
            update = { view ->
                view.text = text
            },
        )
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextView(context).also {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
                    it.setTextColor(color.toArgb())
                }
            },
            update = { view ->
                view.text = text
            },
        )
    }
}
