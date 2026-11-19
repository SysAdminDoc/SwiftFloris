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

package dev.patrickgold.florisboard.ime.nlp.context

import android.content.Context
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * The bundled [ContextModel] for each language that has one, loaded once on [Dispatchers.IO]. The asset is
 * memory-mapped straight out of the APK (app/build.gradle.kts stores `.sflm` assets uncompressed so they can be),
 * so the model holds no heap: its pages are clean file pages the system can drop under memory pressure and read
 * back on the next lookup.
 *
 * Measured budget for the English model (5,941,616 bytes, 5,802 KB in meminfo's units), Galaxy S22 Ultra on
 * Android 16, three runs of `tools/benchmark-ime-memory.ps1 -Scenarios contextModel` on 2026-11-19
 * (docs/benchmark-results/baseline-2026-11-19-context-model-memory.json), loaded minus before, per run:
 * - code pages (`codeKb`): +5,516 to +5,768 KB, median +5,516, under the file's size. This is the mapped file
 *   plus the dex and oat pages of the classes the load touched, and the before value alone varies by about 50 KB.
 * - Java heap: +12 to +44 KB, median +28, inside the 256 KB budget set for it
 * - native heap: +184 to +268 KB, median +212
 * - load: 4.3 to 6.1 ms, median 5.9 ms
 * - lookup: 2.5 to 3.8 us each over 13,800 lookups, median 3.4 us
 * A copy of the model would show up as Java or native heap growth near the file's size, not as code pages.
 */
internal object ContextModelStore {
    private val AssetPaths = mapOf("en" to "lm/en_context.sflm")

    private val mutex = Mutex()

    /** Each language tried so far, null for one whose model failed to load, so it isn't retried every word. */
    @Volatile
    private var models: Map<String, ContextModel?> = emptyMap()

    /** The context model for [language], or null when there isn't one or it couldn't be loaded. */
    suspend fun modelFor(context: Context, language: String): ContextModel? {
        val assetPath = AssetPaths[language] ?: return null
        val known = models
        if (language in known) return known[language]
        return mutex.withLock {
            if (language in models) return@withLock models[language]
            val model = withContext(Dispatchers.IO) { load(context.applicationContext, assetPath) }
            models = models + (language to model)
            model
        }
    }

    private fun load(context: Context, assetPath: String): ContextModel? {
        return try {
            context.assets.openFd(assetPath).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    ContextModel.read(
                        channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length),
                    )
                }
            }
        } catch (error: IOException) {
            flogWarning(LogTopic.DICTIONARY) { "Context model $assetPath couldn't be mapped: $error" }
            null
        } catch (error: IllegalArgumentException) {
            flogWarning(LogTopic.DICTIONARY) { "Context model $assetPath is unreadable: ${error.message}" }
            null
        }
    }
}
