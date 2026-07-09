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

package org.florisboard.lib.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.CurlyArg
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private fun Context.postToast(text: String, duration: Int) {
    val appContext = applicationContext
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(appContext, text, duration).show()
    } else {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, text, duration).show()
        }
    }
}

/**
 * Shows a short toast with specified text.
 *
 * @param text The text to show in the toast popup.
 */
suspend fun Context.showShortToast(text: String): Toast = withContext(Dispatchers.Main.immediate) {
    Toast.makeText(this@showShortToast, text, Toast.LENGTH_SHORT).also { it.show() }
}

/**
 * Shows a short toast with the string resource specified by [id].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 */
suspend fun Context.showShortToast(@StringRes id: Int): Toast {
    val text = this.stringRes(id)
    return showShortToast(text)
}

/**
 * Shows a short toast with the string resource specified by [id], additionally curly formatting the string with
 * supplied arguments [args].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 * @param args The curly arguments which will be filled into the string template identified by [id].
 */
suspend fun Context.showShortToast(@StringRes id: Int, vararg args: CurlyArg): Toast {
    val text = this.stringRes(id, *args)
    return showShortToast(text)
}

/**
 * Shows a long toast with specified text.
 *
 * @param text The text to show in the toast popup.
 */
suspend fun Context.showLongToast(text: String): Toast = withContext(Dispatchers.Main.immediate) {
    Toast.makeText(this@showLongToast, text, Toast.LENGTH_LONG).also { it.show() }
}

/**
 * Shows a long toast with the string resource specified by [id].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 */
suspend fun Context.showLongToast(@StringRes id: Int): Toast {
    val text = this.stringRes(id)
    return showLongToast(text)
}

/**
 * Shows a long toast with the string resource specified by [id], additionally curly formatting the string with
 * supplied arguments [args].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 * @param args The curly arguments which will be filled into the string template identified by [id].
 */
suspend fun Context.showLongToast(@StringRes id: Int, vararg args: CurlyArg): Toast {
    val text = this.stringRes(id, *args)
    return showLongToast(text)
}

/**
 * Posts a short toast without blocking the caller. This is intended for synchronous
 * IME/editor paths which cannot suspend but should not wait on the main thread.
 */
fun Context.postShortToast(text: String) {
    postToast(text, Toast.LENGTH_SHORT)
}

/**
 * Posts a short toast with the string resource specified by [id] without blocking
 * the caller. Must not be used for text derived from typed input.
 */
fun Context.postShortToast(@StringRes id: Int) {
    postToast(stringRes(id), Toast.LENGTH_SHORT)
}




private fun Context.showToastSync(text: String, duration: Int): Toast {
    val appContext = applicationContext
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return Toast.makeText(appContext, text, duration).also { it.show() }
    }
    val toastRef = AtomicReference<Toast>()
    val latch = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        toastRef.set(Toast.makeText(appContext, text, duration).also { it.show() })
        latch.countDown()
    }
    latch.await(2, TimeUnit.SECONDS)
    return toastRef.get() ?: Toast.makeText(appContext, text, duration)
}

@Deprecated(
    "Use suspend showShortToast instead",
    ReplaceWith("showShortToast(text)")
)
fun Context.showShortToastSync(text: String): Toast = showToastSync(text, Toast.LENGTH_SHORT)

@Deprecated(
    "Use suspend showShortToast instead",
    ReplaceWith("showShortToast(id)")
)
fun Context.showShortToastSync(@StringRes id: Int): Toast = showToastSync(stringRes(id), Toast.LENGTH_SHORT)

@Deprecated(
    "Use suspend showShortToast instead",
    ReplaceWith("showShortToast(id, *args)")
)
fun Context.showShortToastSync(@StringRes id: Int, vararg args: CurlyArg): Toast =
    showToastSync(stringRes(id, *args), Toast.LENGTH_SHORT)

@Deprecated(
    "Use suspend showLongToast instead",
    ReplaceWith("showLongToast(text)")
)
fun Context.showLongToastSync(text: String): Toast = showToastSync(text, Toast.LENGTH_LONG)

@Deprecated(
    "Use suspend showLongToast instead",
    ReplaceWith("showLongToast(id)")
)
fun Context.showLongToastSync(@StringRes id: Int): Toast = showToastSync(stringRes(id), Toast.LENGTH_LONG)

@Deprecated(
    "Use suspend showLongToast instead",
    ReplaceWith("showLongToast(id, *args)")
)
fun Context.showLongToastSync(@StringRes id: Int, vararg args: CurlyArg): Toast =
    showToastSync(stringRes(id, *args), Toast.LENGTH_LONG)
