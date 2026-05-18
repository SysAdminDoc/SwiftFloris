/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.mimeTypeFilterOf
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class FlorisCopyToClipboardActivity : ComponentActivity() {
    private var error: CopyToClipboardError? = null
    private var bitmap: Bitmap? = null
    private var copiedToClipboard: Boolean = false
    private val clipboardManager by lazy { systemService(AndroidClipboardManager::class) }
    private val filter = mimeTypeFilterOf("image/*")

    internal enum class CopyToClipboardError {
        UNKNOWN_ERROR,
        TYPE_NOT_SUPPORTED_ERROR;

        @Composable
        fun showError(): String {
            val textId = when (this) {
                UNKNOWN_ERROR -> R.string.send_to_clipboard__unknown_error
                TYPE_NOT_SUPPORTED_ERROR -> R.string.send_to_clipboard__type_not_supported_error
            }
            return stringRes(id = textId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            Content()
        }
    }

    override fun onPause() {
        finish()
        super.onPause()
    }

    private fun handleIntent(intent: Intent) {
        val type = intent.type
        val action = intent.action

        if (Intent.ACTION_SEND != action || type == null) {
            error = CopyToClipboardError.UNKNOWN_ERROR
            return
        }
        if (!filter.matches(type) || !intent.hasExtra(Intent.EXTRA_STREAM)) {
            error = CopyToClipboardError.TYPE_NOT_SUPPORTED_ERROR
            return
        }

        val uri: Uri? =
            if (AndroidVersion.ATLEAST_API33_T) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }

        if (uri == null) {
            error = CopyToClipboardError.TYPE_NOT_SUPPORTED_ERROR
            return
        }
        runCatching {
            copyUriToClipboard(uri)
        }.onSuccess {
            copiedToClipboard = true
            bitmap = runCatching { uriToPreviewBitmap(uri) }.getOrNull()
        }.onFailure {
            error = CopyToClipboardError.UNKNOWN_ERROR
        }
    }

    private fun copyUriToClipboard(uri: Uri) {
        val clip = ClipData.newUri(contentResolver, "image", uri)
        clipboardManager.setPrimaryClip(clip)
    }

    private fun uriToPreviewBitmap(uri: Uri): Bitmap {
        return if (AndroidVersion.ATLEAST_API28_P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                ClipboardPreviewImagePolicy.requireSupportedBounds(size.width, size.height)
                val scale = max(
                    size.width.toFloat() / ClipboardPreviewImagePolicy.MAX_PREVIEW_BITMAP_SIDE,
                    size.height.toFloat() / ClipboardPreviewImagePolicy.MAX_PREVIEW_BITMAP_SIDE,
                )
                if (scale > 1f) {
                    decoder.setTargetSize(
                        (size.width / scale).roundToInt().coerceAtLeast(1),
                        (size.height / scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeLegacyPreviewBitmap(uri)
        }
    }

    private fun decodeLegacyPreviewBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        // If bounds-only decode couldn't determine dimensions, fall back to a sampled
        // full decode with a defensive inSampleSize=2 rather than MediaStore.getBitmap,
        // which decodes at full resolution and can OOM on multi-megapixel inputs from
        // attacker-supplied content:// URIs.
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            val fallbackOptions = BitmapFactory.Options().apply { inSampleSize = 2 }
            return contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, fallbackOptions)
            } ?: error("Cannot decode image preview")
        }
        ClipboardPreviewImagePolicy.requireSupportedBounds(bounds.outWidth, bounds.outHeight)
        val scale = max(
            bounds.outWidth.toFloat() / ClipboardPreviewImagePolicy.MAX_PREVIEW_BITMAP_SIDE,
            bounds.outHeight.toFloat() / ClipboardPreviewImagePolicy.MAX_PREVIEW_BITMAP_SIDE,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (scale > 1f) ceil(scale).toInt() else 1
        }
        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Cannot decode image preview")
    }

    @Composable
    private fun Content() {
        val prefs by FlorisPreferenceStore
        ProvideLocalizedResources(
            resourcesContext = this,
            appName = R.string.app_name,
            forceLayoutDirection = LayoutDirection.Ltr,
        ) {
            val theme by prefs.other.settingsTheme.collectAsState()
            FlorisAppTheme(theme) {
                BottomSheet {
                    Row {
                        Text(
                            text = error?.showError()
                                ?: copiedToClipboard.takeIf { it }?.let {
                                    stringRes(id = R.string.send_to_clipboard__description__copied_image_to_clipboard)
                                }
                                ?: stringRes(R.string.send_to_clipboard__unknown_error),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    bitmap?.let { bmp ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                modifier = Modifier
                                    .padding(start = 64.dp, end = 64.dp, top = 32.dp, bottom = 8.dp),
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BottomSheet(
        content: @Composable ColumnScope.() -> Unit,
    ) {
        ModalBottomSheet(
            modifier = Modifier.navigationBarsPadding(),
            onDismissRequest = { finish() }
        ) {
            Column {
                content()
                Button(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.small,
                    onClick = { finish() },
                    colors = ButtonDefaults.textButtonColors(),
                ) {
                    Text(text = stringRes(id = R.string.action__ok))
                }
            }
        }
    }
}
