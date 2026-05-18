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
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.florisboard.themeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BenchmarkThemeSwitchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val input = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START
            hint = "SwiftFloris theme benchmark input"
            imeOptions = EditorInfo.IME_ACTION_NONE
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 8
            setSingleLine(false)
        }
        setContentView(
            FrameLayout(this).apply {
                setPadding(32, 32, 32, 32)
                addView(
                    input,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )
        input.post {
            input.requestFocus()
            getSystemService(InputMethodManager::class.java)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        lifecycleScope.launch {
            try {
                val app = applicationContext as FlorisApplication
                withTimeout(10_000) {
                    app.preferenceStoreLoaded.filter { it }.first()
                }

                val themeManager by themeManager()
                val targets = listOf(
                    extCoreTheme("swiftkey_pure_light"),
                    extCoreTheme("m3e_nord_dark"),
                    extCoreTheme("m3e_swiftkey_pure_dark"),
                    extCoreTheme("m3e_nord_dark"),
                    extCoreTheme("swiftkey_pure_light"),
                )
                withTimeout(10_000) {
                    themeManager.indexedThemeConfigs.filter { indexed ->
                        targets.distinct().all { it in indexed.first.keys }
                    }.first()
                }

                delay(1_200)
                val seenThemes = mutableSetOf<String>()
                for ((index, themeName) in targets.withIndex()) {
                    val expectedCacheHit = themeName.toString() in seenThemes
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    themeManager.updateActiveThemeForBenchmark(themeName)
                    val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                    Log.i(
                        "SwiftFlorisPerf",
                        "swiftfloris.theme.benchmarkStepMs=$durationMs " +
                            "step=${index + 1} theme=$themeName expectedCacheHit=$expectedCacheHit",
                    )
                    seenThemes += themeName.toString()
                    delay(250)
                }
                setResult(Activity.RESULT_OK)
            } catch (error: Throwable) {
                Log.e("SwiftFlorisPerf", "swiftfloris.theme.benchmarkFailed", error)
                setResult(Activity.RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }
}
