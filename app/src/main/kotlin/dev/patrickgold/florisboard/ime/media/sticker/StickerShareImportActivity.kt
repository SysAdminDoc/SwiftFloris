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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showLongToast

class StickerShareImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = sharedImageUri(intent)
        if (uri == null) {
            lifecycleScope.launch {
                showLongToast(R.string.prefs__media__stickers_pack_failure_unsupported)
                finish()
            }
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                LocalStickerPackRepository.importSharedImage(this@StickerShareImportActivity, uri)
            }
            when (result) {
                is LocalStickerPackResult.Success -> {
                    evictStickerBitmapCache()
                    showLongToast(R.string.sticker__share_import__success)
                }
                is LocalStickerPackResult.Failure -> showLongToast(result.reason.messageStringId())
            }
            finish()
        }
    }

    private fun sharedImageUri(intent: Intent): Uri? {
        val type = intent.type ?: return null
        if (intent.action != Intent.ACTION_SEND || !type.startsWith("image/")) return null
        if (!intent.hasExtra(Intent.EXTRA_STREAM)) return null
        return if (AndroidVersion.ATLEAST_API33_T) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun LocalStickerPackFailure.messageStringId(): Int {
        return when (this) {
            LocalStickerPackFailure.UNSUPPORTED_MIME_TYPE ->
                R.string.prefs__media__stickers_pack_failure_unsupported
            LocalStickerPackFailure.OVERSIZED ->
                R.string.prefs__media__stickers_pack_failure_oversized
            LocalStickerPackFailure.EMPTY ->
                R.string.prefs__media__stickers_pack_failure_empty
            LocalStickerPackFailure.INVALID_ARCHIVE ->
                R.string.prefs__media__stickers_pack_failure_invalid
            LocalStickerPackFailure.TOO_MANY_STICKERS ->
                R.string.prefs__media__stickers_pack_failure_too_many
            LocalStickerPackFailure.IO_ERROR ->
                R.string.prefs__media__stickers_pack_failure_io
        }
    }
}
