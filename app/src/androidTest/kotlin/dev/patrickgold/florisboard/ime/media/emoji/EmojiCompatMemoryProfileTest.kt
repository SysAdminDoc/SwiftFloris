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

package dev.patrickgold.florisboard.ime.media.emoji

import android.os.Debug
import android.util.Log
import androidx.emoji2.text.EmojiCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmojiCompatMemoryProfileTest {
    @Test
    fun profileLazyReplaceAllInstanceMemory() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            FlorisEmojiCompat.init(context)

            val startup = MemorySnapshot.capture("startup")
            val noReplaceFlow = FlorisEmojiCompat.getAsFlow(replaceAll = false)
            val noReplace = noReplaceFlow.awaitLoaded()
            assertNotNull("Default EmojiCompat instance did not load within the profile timeout", noReplace)
            val afterNoReplace = MemorySnapshot.capture("after_no_replace")

            val replaceAllFlow = FlorisEmojiCompat.getAsFlow(replaceAll = true)
            val replaceAll = replaceAllFlow.awaitLoaded()
            assertNotNull("Replace-all EmojiCompat instance did not load within the profile timeout", replaceAll)
            val afterReplaceAll = MemorySnapshot.capture("after_replace_all")

            val replaceAllDeltaPssKb = afterReplaceAll.totalPssKb - afterNoReplace.totalPssKb
            val replaceAllDeltaHeapKb = afterReplaceAll.javaHeapUsedKb - afterNoReplace.javaHeapUsedKb
            Log.i(
                TAG,
                "EmojiCompat memory profile: " +
                    "${startup.toLogString()}, " +
                    "${afterNoReplace.toLogString()}, " +
                    "${afterReplaceAll.toLogString()}, " +
                    "replaceAllDeltaPssKb=$replaceAllDeltaPssKb, " +
                    "replaceAllDeltaHeapKb=$replaceAllDeltaHeapKb",
            )
        }
    }

    private suspend fun StateFlow<EmojiCompat?>.awaitLoaded(): EmojiCompat? {
        value?.let { return it }
        return withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) {
            filterNotNull().first()
        }
    }

    private data class MemorySnapshot(
        val label: String,
        val totalPssKb: Int,
        val dalvikPssKb: Int,
        val nativePssKb: Int,
        val otherPssKb: Int,
        val javaHeapUsedKb: Long,
    ) {
        fun toLogString(): String {
            return "$label(totalPssKb=$totalPssKb, dalvikPssKb=$dalvikPssKb, " +
                "nativePssKb=$nativePssKb, otherPssKb=$otherPssKb, javaHeapUsedKb=$javaHeapUsedKb)"
        }

        companion object {
            fun capture(label: String): MemorySnapshot {
                Runtime.getRuntime().gc()
                Thread.sleep(GC_SETTLE_MILLIS)

                val memoryInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfo)
                val runtime = Runtime.getRuntime()
                val javaHeapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
                return MemorySnapshot(
                    label = label,
                    totalPssKb = memoryInfo.totalPss,
                    dalvikPssKb = memoryInfo.dalvikPss,
                    nativePssKb = memoryInfo.nativePss,
                    otherPssKb = memoryInfo.otherPss,
                    javaHeapUsedKb = javaHeapUsedKb,
                )
            }
        }
    }

    private companion object {
        const val TAG = "EmojiCompatProfile"
        const val LOAD_TIMEOUT_MILLIS = 15_000L
        const val GC_SETTLE_MILLIS = 250L
    }
}
