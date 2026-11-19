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

package dev.patrickgold.florisboard.app.ext

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.io.FlorisRef
import dev.patrickgold.florisboard.lib.io.ZipUtils
import java.io.File
import java.io.FileNotFoundException
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisSpacing
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.FsDir

/** Why an extension couldn't be unpacked for editing, in terms that are safe to show. */
internal enum class ExtensionPreparationFailure {
    /** The extension's file is gone or can't be opened. */
    Unreadable,

    /** The file opened but isn't a usable extension archive. */
    Damaged,

    /** This kind of extension, or where it lives, has no editor. */
    Unsupported,
}

/**
 * A preparation failure with a [failure] reason the screen can show. The message names only the
 * reason, so nothing from the [cause] (paths, entry names, class names) reaches the UI.
 */
internal class ExtensionPreparationException(
    val failure: ExtensionPreparationFailure,
    cause: Throwable? = null,
) : Exception("Extension preparation failed: $failure", cause)

/**
 * Unpacks an installed extension from [sourceRef] into [dstDir] for the editor. Every failure
 * comes out as an [ExtensionPreparationException], with the original error kept as its cause for
 * the log.
 */
internal fun unpackExtensionSource(context: Context, sourceRef: FlorisRef?, dstDir: FsDir) {
    if (sourceRef == null || !(sourceRef.isAssets || sourceRef.isCache || sourceRef.isInternal)) {
        throw ExtensionPreparationException(ExtensionPreparationFailure.Unsupported)
    }
    if (!sourceRef.isAssets) {
        val file = File(sourceRef.absolutePath(context))
        if (!file.isFile || !file.canRead()) {
            throw ExtensionPreparationException(ExtensionPreparationFailure.Unreadable)
        }
    }
    ZipUtils.unzip(context, sourceRef, dstDir).onFailure { error ->
        val failure = if (error is FileNotFoundException) {
            ExtensionPreparationFailure.Unreadable
        } else {
            ExtensionPreparationFailure.Damaged
        }
        throw ExtensionPreparationException(failure, error)
    }
}

/**
 * Runs [prepare] once, and again on Retry, then shows [content] with its result. A failure is
 * logged in full and shown as [ExtensionPreparationFailedScreen].
 */
@Composable
internal fun <W : Any> PreparedExtensionWorkspace(
    prepare: () -> W,
    onBack: () -> Unit,
    content: @Composable (W) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val result = remember(attempt) {
        runCatching(prepare).onFailure { error ->
            flogError { "Could not prepare the extension editor: ${error.stackTraceToString()}" }
        }
    }
    result.onSuccess { workspace ->
        content(workspace)
    }.onFailure { error ->
        ExtensionPreparationFailedScreen(
            failure = (error as? ExtensionPreparationException)?.failure,
            onRetry = { attempt++ },
            onBack = onBack,
        )
    }
}

/**
 * Says why the editor couldn't open, without any of the error's own text, and offers Retry and
 * Back. A null [failure] is an error nobody classified, which gets the general explanation.
 */
@Composable
internal fun ExtensionPreparationFailedScreen(
    failure: ExtensionPreparationFailure?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) = FlorisScreen {
    title = stringRes(R.string.ext__editor__preparation_failed_title)

    content {
        FlorisErrorCard(
            modifier = Modifier.padding(FlorisSpacing.Large),
            text = stringRes(R.string.ext__editor__preparation_failed_title),
            secondaryText = stringRes(
                when (failure) {
                    ExtensionPreparationFailure.Unreadable -> R.string.ext__editor__preparation_failed_unreadable
                    ExtensionPreparationFailure.Damaged -> R.string.ext__editor__preparation_failed_damaged
                    ExtensionPreparationFailure.Unsupported -> R.string.ext__editor__preparation_failed_unsupported
                    null -> R.string.ext__editor__preparation_failed_other
                },
            ),
        )
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(text = stringRes(R.string.action__back), onClick = onBack)
            ButtonBarButton(text = stringRes(R.string.action__retry), onClick = onRetry)
        }
    }
}
