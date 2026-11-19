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

package dev.patrickgold.florisboard.lib.compose

import android.os.Build
import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.atomic.AtomicInteger

/** Where [CompositionTraceSection] sends its begin and end, so a test can count them. */
internal interface AsyncTraceSink {
    fun begin(name: String, cookie: Int)
    fun end(name: String, cookie: Int)
}

/** Perfetto's async sections. Android 9 and older have none, and the benchmarks run on newer phones. */
internal object AndroidAsyncTraceSink : AsyncTraceSink {
    override fun begin(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection(name, cookie)
    }

    override fun end(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection(name, cookie)
    }
}

/** The sink [rememberCompositionTraceSection] reports to. Tests provide a counting one around real composables. */
internal val LocalAsyncTraceSink = staticCompositionLocalOf<AsyncTraceSink> { AndroidAsyncTraceSink }

/**
 * One composition of the caller, timed as an async trace section. Compose allows no try/finally around composable
 * calls, so the caller calls [end] after its content, and a composition that throws or is dropped before that ends
 * the section when Compose abandons this remembered object. An async section is matched by name and cookie, not
 * by the thread's stack of sections, so ending it late never closes another section. Macrobenchmark's
 * TraceSectionMetric reads async sections by name like plain ones.
 */
internal class CompositionTraceSection(
    private val name: String,
    private val sink: AsyncTraceSink,
) : RememberObserver {
    private val cookie = NextCookie.incrementAndGet()
    private var isOpen = true

    init {
        sink.begin(name, cookie)
    }

    fun end() {
        if (isOpen) {
            isOpen = false
            sink.end(name, cookie)
        }
    }

    override fun onRemembered() = Unit

    override fun onForgotten() = end()

    override fun onAbandoned() = end()

    private companion object {
        val NextCookie = AtomicInteger()
    }
}

/**
 * Begins a section for this composition. Every composition gets its own object, since Compose only reports an
 * abandoned composition to the objects it remembered in that pass.
 */
@Composable
internal fun rememberCompositionTraceSection(
    name: String,
    sink: AsyncTraceSink = LocalAsyncTraceSink.current,
): CompositionTraceSection {
    return remember(Any()) { CompositionTraceSection(name, sink) }
}
