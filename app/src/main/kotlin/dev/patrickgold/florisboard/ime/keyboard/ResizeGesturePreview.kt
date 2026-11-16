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

package dev.patrickgold.florisboard.ime.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import dev.patrickgold.florisboard.ime.window.ImeWindowController
import dev.patrickgold.florisboard.ime.window.ImeWindowSpec
import kotlin.math.roundToInt

/**
 * The key row and Smartbar row heights a window spec gives the keyboard.
 */
internal data class ImeRowHeights(val row: Dp, val smartbar: Dp) {
    companion object {
        fun of(spec: ImeWindowSpec): ImeRowHeights {
            return ImeRowHeights(
                row = spec.calcRowHeight(spec.props.keyboardHeight),
                smartbar = spec.calcSmartbarRowHeight(spec.props.keyboardHeight),
            )
        }
    }
}

/**
 * Keeps a resize drag cheap (issue #23). While the drag runs, [ProvideKeyboardRowBaseHeight] holds the rows
 * at the height the drag began with, and [resizeGesturePreview] stretches a picture of them to the height the
 * drag has reached. The keys are laid out again once, when the drag ends, instead of on every frame.
 */
internal object ResizeGesturePreview {
    /**
     * How much taller the keyboard is at [live] than at [start], for [keyboardRowCount] key rows and
     * [smartbarRowCount] Smartbar rows. Negative when the drag made it shorter.
     */
    fun extraHeight(
        start: ImeRowHeights,
        live: ImeRowHeights,
        keyboardRowCount: Int,
        smartbarRowCount: Int,
    ): Dp {
        return (live.row - start.row) * keyboardRowCount + (live.smartbar - start.smartbar) * smartbarRowCount
    }

    /**
     * The height, in px, to report for content measured at [measuredPx] once stretched by [extraPx], kept
     * within [minHeightPx]..[maxHeightPx] and never below 1 px.
     */
    fun stretchedHeightPx(measuredPx: Int, extraPx: Int, minHeightPx: Int, maxHeightPx: Int): Int {
        return (measuredPx + extraPx)
            .coerceAtMost(maxHeightPx)
            .coerceAtLeast(minHeightPx)
            .coerceAtLeast(1)
    }
}

/**
 * The window spec the keys lay out with: the one a resize drag began with while the drag runs, else the live
 * one. Key margins follow the keyboard height, so the live spec would lay out every key again on each frame of
 * the drag (issue #23). The live spec isn't read during the drag, so the drag doesn't recompose the caller.
 */
@Composable
internal fun rememberKeyLayoutWindowSpec(windowController: ImeWindowController): State<ImeWindowSpec> {
    val liveSpec = windowController.activeWindowSpec.collectAsState()
    val editorState = windowController.editor.state.collectAsState()
    return remember(windowController, liveSpec, editorState) {
        derivedStateOf {
            windowController.editor.gestureStartSpec.takeIf { editorState.value.isResizeGesture } ?: liveSpec.value
        }
    }
}

/**
 * A picture of the keyboard taken as a resize drag starts, for [resizeGesturePreview] to stretch while the
 * drag runs. Drawing the keys themselves each frame, even inside an offscreen layer, still drew every key
 * again on each frame on the S22 (issue #23).
 */
@Stable
internal class ResizePreviewSnapshot(val layer: GraphicsLayer, val isDragging: State<Boolean>) {
    var image by mutableStateOf<ImageBitmap?>(null)

    /** Whether a frame of the current drag recorded the keyboard into [layer]. */
    var isRecorded = false
}

/** The [ResizePreviewSnapshot] for the resize drag [extraHeight] describes (see [resizeGesturePreview]). */
@Composable
internal fun rememberResizePreviewSnapshot(extraHeight: State<Dp?>): ResizePreviewSnapshot {
    val layer = rememberGraphicsLayer()
    val snapshot = remember(layer, extraHeight) {
        ResizePreviewSnapshot(layer, derivedStateOf { extraHeight.value != null })
    }
    val isDragging by snapshot.isDragging
    LaunchedEffect(snapshot, isDragging) {
        snapshot.image = null
        if (!isDragging) {
            return@LaunchedEffect
        }
        // The drag's first frame records the keyboard into the layer as it draws it live. A drag that starts
        // before a frame drew the last one's end would otherwise find the last drag's recording.
        snapshot.isRecorded = false
        while (!snapshot.isRecorded) {
            withFrameNanos { }
        }
        snapshot.image = layer.toImageBitmap()
    }
    return snapshot
}

/**
 * Stretches the keyboard to the height a resize drag has reached while [ProvideKeyboardRowBaseHeight] holds
 * its rows at the height the drag began with. [extraHeight] is [FlorisImeSizing.resizePreviewExtraHeight],
 * null when no resize drag is running. It's read only while measuring, so a drag frame measures this one
 * node and moves a layer instead of measuring every key. Once [snapshot] has its picture of the keyboard,
 * the layer draws only that picture, so each frame scales one image instead of drawing every key.
 */
internal fun Modifier.resizeGesturePreview(
    extraHeight: State<Dp?>,
    snapshot: ResizePreviewSnapshot,
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val extraPx = extraHeight.value?.roundToPx()
    if (extraPx == null || placeable.height <= 0) {
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    } else {
        val height = ResizeGesturePreview.stretchedHeightPx(
            measuredPx = placeable.height,
            extraPx = extraPx,
            minHeightPx = constraints.minHeight,
            maxHeightPx = constraints.maxHeight,
        )
        val scale = height.toFloat() / placeable.height
        layout(placeable.width, height) {
            placeable.placeWithLayer(0, 0) {
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}.drawWithContent {
    if (!snapshot.isDragging.value) {
        snapshot.isRecorded = false
        drawContent()
        return@drawWithContent
    }
    val image = snapshot.image
    if (image != null) {
        // A side or corner handle changes the width too, so the picture is stretched to the live size.
        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    } else {
        snapshot.layer.record { this@drawWithContent.drawContent() }
        snapshot.isRecorded = true
        drawLayer(snapshot.layer)
    }
}
