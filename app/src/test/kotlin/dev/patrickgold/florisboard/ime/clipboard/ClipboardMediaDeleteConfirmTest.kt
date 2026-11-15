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

package dev.patrickgold.florisboard.ime.clipboard

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.shouldBe
import java.io.File
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Issue #25: deleting an image or video clip used a Compose Dialog that was only
 * composed by the "history disabled" view, so Delete did nothing in the list and
 * turning history off afterwards added a dialog window from the input method
 * window, which crashed with BadTokenException. The confirmation is now drawn
 * inside the keyboard.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardMediaDeleteConfirmTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun `media clips ask before deleting and text clips do not`() {
        ClipboardMediaDeletePolicy.needsConfirmation(ItemType.IMAGE) shouldBe true
        ClipboardMediaDeletePolicy.needsConfirmation(ItemType.VIDEO) shouldBe true
        ClipboardMediaDeletePolicy.needsConfirmation(ItemType.TEXT) shouldBe false
        ClipboardMediaDeletePolicy.descriptionRes(ItemType.IMAGE) shouldBe R.string.clipboard__item_description_image
        ClipboardMediaDeletePolicy.descriptionRes(ItemType.VIDEO) shouldBe R.string.clipboard__item_description_video
    }

    @Test
    fun `media delete confirmation renders inside the keyboard without a dialog window`() {
        var confirmed by mutableIntStateOf(0)
        var pending by mutableStateOf(true)
        setPanel(onDismiss = { pending = false }, onConfirm = { confirmed++; pending = false }) { pending }

        composeRule.onNodeWithText("Delete this image?").assertExists()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        confirmed shouldBe 1
        composeRule.onNodeWithText("Delete this image?").assertDoesNotExist()
    }

    @Test
    fun `cancel keeps the clip`() {
        var confirmed by mutableIntStateOf(0)
        var pending by mutableStateOf(true)
        setPanel(onDismiss = { pending = false }, onConfirm = { confirmed++ }) { pending }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        confirmed shouldBe 0
        composeRule.onNodeWithText("Delete this image?").assertDoesNotExist()
    }

    private fun setPanel(onDismiss: () -> Unit, onConfirm: () -> Unit, isShown: () -> Boolean) {
        composeRule.setContent {
            val theme = rememberSnyggTheme(remember { loadStylesheet() })
            MaterialTheme {
                ProvideSnyggTheme(theme) {
                    if (isShown()) {
                        ClipboardConfirmPanel(
                            modifier = Modifier.size(width = 360.dp, height = 240.dp),
                            message = "Delete this image?",
                            dismissLabel = "Cancel",
                            confirmLabel = "Delete",
                            onDismiss = onDismiss,
                            onConfirm = onConfirm,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun loadStylesheet(): SnyggStylesheet {
        val name = "floris_night.json"
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name"),
        ).first { it.exists() }
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }
}
