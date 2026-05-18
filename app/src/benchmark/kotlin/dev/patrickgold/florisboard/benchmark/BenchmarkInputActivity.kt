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
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout

class BenchmarkInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val input = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START
            hint = "SwiftFloris benchmark input"
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
    }
}
