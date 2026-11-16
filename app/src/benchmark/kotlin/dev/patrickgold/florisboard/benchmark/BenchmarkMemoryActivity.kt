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

package dev.patrickgold.florisboard.benchmark

import android.app.Activity
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.media.emoji.EmojiData
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSearch
import dev.patrickgold.florisboard.ime.media.sticker.loadStickerBitmap
import dev.patrickgold.florisboard.ime.nlp.context.ContextModelStore
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingGesture
import dev.patrickgold.florisboard.ime.text.gestures.StatisticalGlideTypingClassifier
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Measures the process's memory around one keyboard workload, so the Android 17 per-app memory
 * limiter work has a baseline to compare against. Run one scenario per fresh process
 * (tools/benchmark-ime-memory.ps1 force-stops the package between runs).
 *
 * Each scenario logs three samples on the SwiftFlorisPerf tag: before the workload, after it, and
 * after the app's background trim. Samples are taken after a GC. Nothing typed by the user is read.
 */
class BenchmarkMemoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra(ExtraScenario).orEmpty()
        setContentView(TextView(this).apply { text = scenario })

        lifecycleScope.launch {
            try {
                when (scenario) {
                    "dictionary" -> dictionary()
                    "glide" -> glide()
                    "emojiSearch" -> emojiSearch()
                    "clipboard" -> clipboard()
                    "sticker" -> sticker()
                    "contextModel" -> contextModel()
                    else -> error("unknown scenario '$scenario'")
                }
                Log.i(Tag, "swiftfloris.mem.done scenario=$scenario")
                setResult(Activity.RESULT_OK)
            } catch (error: Throwable) {
                Log.e(Tag, "swiftfloris.mem.benchmarkFailed scenario=$scenario", error)
                setResult(Activity.RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }

    private suspend fun dictionary() {
        record("dictionary", "before")
        val provider = LatinLanguageProvider(this)
        val startedAt = SystemClock.elapsedRealtimeNanos()
        provider.preload(Subtype.DEFAULT)
        val loadMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
        record("dictionary", "loaded", "loadMs=$loadMs")
        trimApp()
        record("dictionary", "trimmed")
        provider.destroy()
    }

    private suspend fun glide() {
        record("glide", "before")
        val classifier = StatisticalGlideTypingClassifier(this)
        val keysByChar = qwertyKeys()
        withContext(Dispatchers.Default) { classifier.setLayout(keysByChar.values.toList(), Subtype.DEFAULT) }
        var decoded = 0
        withContext(Dispatchers.Default) {
            for (word in GlideWords) {
                classifier.clear()
                gesturePoints(word, keysByChar).forEach(classifier::addGesturePoint)
                if (classifier.getSuggestions(8, true).isNotEmpty()) decoded++
            }
        }
        record("glide", "loaded", "words=${GlideWords.size} decoded=$decoded")
        classifier.trimMemory()
        trimApp()
        record("glide", "trimmed")
    }

    private suspend fun emojiSearch() {
        record("emojiSearch", "before")
        val data = EmojiData.get(this, FlorisLocale.from("en"))
        var resultCount = 0
        withContext(Dispatchers.Default) {
            for (query in EmojiQueries) {
                resultCount += EmojiSearch.results(data.byCategory, query).size
            }
        }
        record("emojiSearch", "loaded", "queries=${EmojiQueries.size} results=$resultCount")
        trimApp()
        record("emojiSearch", "trimmed")
    }

    private suspend fun clipboard() {
        val clipboard = clipboardManager().value
        // Waits for the history store to open, so the inserts below aren't dropped.
        clipboard.snapshotHistoryForRestore()
        clipboard.clearFullHistory()
        waitForHistoryToSettle()
        record("clipboard", "before")
        repeat(ClipCount) { index ->
            clipboard.insertClip(ClipboardItem.text("Benchmark clip $index " + ClipFiller))
        }
        val historySize = waitForHistoryToSettle()
        record("clipboard", "loaded", "inserted=$ClipCount historySize=$historySize")
        trimApp()
        record("clipboard", "trimmed")
        clipboard.clearFullHistory()
    }

    private suspend fun sticker() {
        val files = stickerFiles()
        record("sticker", "before")
        var decoded = 0
        withContext(Dispatchers.IO) {
            for (file in files) {
                if (loadStickerBitmap(this@BenchmarkMemoryActivity, Uri.fromFile(file).toString()) != null) decoded++
            }
        }
        record("sticker", "loaded", "files=${files.size} decoded=$decoded")
        trimApp()
        record("sticker", "trimmed")
    }

    /**
     * Maps the English context model and scores every word of [ContextSentences] after the two before it, the
     * lookups typing those sentences makes. The model is a memory-mapped APK asset, so what it costs shows up as
     * clean file pages (codeKb), not heap.
     */
    private suspend fun contextModel() {
        record("contextModel", "before")
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val model = checkNotNull(ContextModelStore.modelFor(this, "en")) { "no English context model" }
        val loadMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
        val words = ContextSentences.flatMap { it.split(' ') }
        var lookups = 0
        var scored = 0
        val lookupStartedAt = SystemClock.elapsedRealtimeNanos()
        withContext(Dispatchers.Default) {
            repeat(ContextRounds) {
                for (index in 1 until words.size) {
                    lookups++
                    if (model.score(words.getOrNull(index - 2), words[index - 1], words[index]) != null) scored++
                }
            }
        }
        val lookupUs = (SystemClock.elapsedRealtimeNanos() - lookupStartedAt) / 1_000.0 / lookups
        record("contextModel", "loaded", "loadMs=$loadMs lookups=$lookups scored=$scored usPerLookup=$lookupUs")
        trimApp()
        record("contextModel", "trimmed")
    }

    /** Keeps the generated sticker files between runs so only the first run pays for writing them. */
    private suspend fun stickerFiles(): List<File> = withContext(Dispatchers.IO) {
        val dir = File(noBackupFilesDir, "bench-stickers").apply { mkdirs() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        List(StickerCount) { index ->
            File(dir, "sticker-$index.png").also { file ->
                if (file.length() > 0) return@also
                val bitmap = Bitmap.createBitmap(StickerEdgePx, StickerEdgePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.HSVToColor(floatArrayOf(index * 5f % 360f, 0.5f, 0.9f)))
                paint.color = Color.HSVToColor(floatArrayOf((index * 5f + 180f) % 360f, 0.8f, 0.6f))
                canvas.drawCircle(StickerEdgePx / 2f, StickerEdgePx / 2f, StickerEdgePx / 3f, paint)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private suspend fun waitForHistoryToSettle(): Int {
        val clipboard = clipboardManager().value
        var lastSize = -1
        var stablePolls = 0
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (stablePolls < 10 && SystemClock.elapsedRealtime() < deadline) {
            delay(100)
            val size = clipboard.currentHistory.all.size
            if (size == lastSize) stablePolls++ else stablePolls = 0
            lastSize = size
        }
        return lastSize
    }

    private fun trimApp() {
        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    }

    private suspend fun record(scenario: String, phase: String, detail: String = "") {
        val sample = withContext(Dispatchers.Default) {
            repeat(2) {
                Runtime.getRuntime().gc()
                System.runFinalization()
                delay(200)
            }
            Debug.MemoryInfo().also(Debug::getMemoryInfo)
        }
        Log.i(
            Tag,
            "swiftfloris.mem.result scenario=$scenario phase=$phase pssKb=${sample.totalPss} " +
                "javaHeapKb=${sample.stat("summary.java-heap")} " +
                "nativeHeapKb=${sample.stat("summary.native-heap")} " +
                "graphicsKb=${sample.stat("summary.graphics")} " +
                "codeKb=${sample.stat("summary.code")} $detail".trimEnd(),
        )
    }

    private fun Debug.MemoryInfo.stat(name: String): Int = getMemoryStat(name)?.toIntOrNull() ?: -1

    companion object {
        const val ExtraScenario = "dev.patrickgold.florisboard.benchmark.MEMORY_SCENARIO"
        private const val Tag = "SwiftFlorisPerf"

        private const val ClipCount = 200
        private val ClipFiller = "lorem ipsum dolor sit amet ".repeat(40)

        // 72 files against a 64 entry cache, each decoded down to 512 px (1 MB as ARGB_8888).
        private const val StickerCount = 72
        private const val StickerEdgePx = 1024

        private val GlideWords = listOf(
            "hello", "world", "keyboard", "typing", "quick", "brown", "the", "memory", "android", "phone",
        )
        private const val ContextRounds = 200
        private val ContextSentences = listOf(
            "i got a letter from my mother this morning",
            "we went to the store to buy some bread and milk",
            "can you tell me what time it is",
            "she said that she would be here at noon",
            "the weather has been really nice all week",
            "i think we should talk about it tomorrow",
            "do you want to come over for dinner tonight",
            "he left his phone at the office again",
        )
        private val EmojiQueries = listOf(
            "smile", "heart", "cat", "dog", "fire", "thumbs", "party", "sad", "laugh", "flag",
            "sun", "moon", "food", "car", "star", "hand", "face", "tree", "music", "ball",
        )

        private const val KeyWidthPx = 108f
        private const val KeyHeightPx = 160f

        private fun qwertyKeys(): Map<Char, TextKey> {
            val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to KeyWidthPx / 2, "zxcvbnm" to KeyWidthPx * 1.5f)
            return rows.flatMapIndexed { rowIndex, (letters, offset) ->
                letters.mapIndexed { column, c ->
                    c to TextKey(TextKeyData(code = c.code, label = c.toString())).also { key ->
                        key.visibleBounds.apply {
                            left = offset + column * KeyWidthPx
                            top = rowIndex * KeyHeightPx
                            right = left + KeyWidthPx
                            bottom = top + KeyHeightPx
                        }
                    }
                }
            }.toMap()
        }

        /** A straight-line swipe through the centers of the word's keys, 8 points per segment. */
        private fun gesturePoints(word: String, keysByChar: Map<Char, TextKey>): List<GlideTypingGesture.Detector.Position> {
            val centers = word.mapNotNull { keysByChar[it]?.visibleBounds }
                .map { GlideTypingGesture.Detector.Position(it.center.x, it.center.y) }
            if (centers.size < 2) return centers
            return centers.zipWithNext().flatMap { (from, to) ->
                List(8) { step ->
                    val t = step / 8f
                    GlideTypingGesture.Detector.Position(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
                }
            } + centers.last()
        }
    }
}
