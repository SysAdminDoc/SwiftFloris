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

package dev.patrickgold.florisboard.app.settings.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsSearchAccessibilityContractTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun accessibilityStringsResolveToRealNonBlankValues() {
        listOf(
            R.string.settings__search__field_content_description,
            R.string.settings__search__empty_query_a11y,
            R.string.settings__search__no_results_a11y,
            R.string.settings__search__results_count_a11y,
            R.string.settings__search__result_a11y,
            R.string.settings__search__result_a11y_no_summary,
        ).forEach { resId ->
            val value = context.getString(resId)
            assertTrue(value.isNotBlank(), "Search accessibility string $resId must be non-blank")
            assertTrue(value != "res-$resId", "Search accessibility string $resId must not use test fallback")
        }
    }
}
