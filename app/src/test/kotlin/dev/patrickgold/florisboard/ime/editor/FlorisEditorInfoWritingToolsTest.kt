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

package dev.patrickgold.florisboard.ime.editor

import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * `EditorInfo.isWritingToolsEnabled()` arrived in Android 16. Below that an
 * editor has no way to express the objection, so the wrapper has to answer
 * `true` rather than read a field that is not there.
 */
@RunWith(AndroidJUnit4::class)
class FlorisEditorInfoWritingToolsTest {

    @Test
    @Config(sdk = [36])
    fun anEditorOnApi36CanForbidRewriting() {
        val editorInfo = EditorInfo().apply { isWritingToolsEnabled = false }

        assertTrue(
            !FlorisEditorInfo.wrap(editorInfo).isWritingToolsEnabled,
            "an editor that set the flag to false must be reported as forbidding rewriting",
        )
    }

    @Test
    @Config(sdk = [36])
    fun anEditorOnApi36ThatAllowsRewritingIsReportedAsAllowing() {
        val editorInfo = EditorInfo().apply { isWritingToolsEnabled = true }

        assertTrue(FlorisEditorInfo.wrap(editorInfo).isWritingToolsEnabled)
    }

    @Test
    @Config(sdk = [35])
    fun anEditorBelowApi36AlwaysAllows() {
        // Neither the getter nor the setter exists on API 35, so touching the
        // flag there raises NoSuchMethodError. That is what makes this
        // assertion worth having: dropping the version guard turns this from a
        // passing test into a crash rather than into a wrong answer.
        assertTrue(
            FlorisEditorInfo.wrap(EditorInfo()).isWritingToolsEnabled,
            "below API 36 the wrapper must answer true without consulting the flag",
        )
    }
}
