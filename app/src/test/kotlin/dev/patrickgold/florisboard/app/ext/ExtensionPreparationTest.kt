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

package dev.patrickgold.florisboard.app.ext

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.lib.io.FlorisRef
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Opening an installed extension in the editor unpacks its file first. When that fails, the
 * screen used to print the stack trace, private paths included. Each fixture here goes through
 * the real unpack and has to come out as a plain, announced explanation with Retry and Back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExtensionPreparationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private val context get() = composeRule.activity
    private lateinit var workDir: File
    private var backPresses = 0
    private var attempts = 0

    @Before
    fun setUp() {
        workDir = File(context.cacheDir, "extension-preparation-test").apply {
            deleteRecursively()
            mkdirs()
        }
        File(context.filesDir, FixtureDir).apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun aDamagedArchiveIsExplainedWithoutItsError() {
        fixtureFile("damaged.flex").writeText("this is not a zip archive")

        showEditorFor(FlorisRef.internal("$FixtureDir/damaged.flex"))

        assertExplains(R.string.ext__editor__preparation_failed_damaged)
    }

    @Test
    fun aMissingFileIsExplainedAndRetryOpensItOnceItIsBack() {
        val source = FlorisRef.internal("$FixtureDir/missing.flex")

        showEditorFor(source)
        assertExplains(R.string.ext__editor__preparation_failed_unreadable)

        writeExtensionArchive(fixtureFile("missing.flex"))
        composeRule.onNodeWithText(string(R.string.action__retry)).performClick()
        composeRule.waitForIdle()

        attempts shouldBe 2
        composeRule.onNodeWithText("Editor ready").assertExists()
    }

    @Test
    fun anUnsupportedSourceIsExplainedAndBackLeaves() {
        showEditorFor(FlorisRef.fromUrl("https://example.com/theme.flex"))

        assertExplains(R.string.ext__editor__preparation_failed_unsupported)
        composeRule.onNodeWithText(string(R.string.action__back)).performClick()
        composeRule.waitForIdle()

        backPresses shouldBe 1
    }

    @Test
    fun retryOnAFileThatIsStillDamagedTriesAgainAndExplainsAgain() {
        fixtureFile("still-damaged.flex").writeText("PK but not really")

        showEditorFor(FlorisRef.internal("$FixtureDir/still-damaged.flex"))
        composeRule.onNodeWithText(string(R.string.action__retry)).performClick()
        composeRule.waitForIdle()

        attempts shouldBe 2
        assertExplains(R.string.ext__editor__preparation_failed_damaged)
    }

    @Test
    fun aGoodArchiveOpensTheEditor() {
        writeExtensionArchive(fixtureFile("good.flex"))

        showEditorFor(FlorisRef.internal("$FixtureDir/good.flex"))

        composeRule.onNodeWithText("Editor ready").assertExists()
        File(workDir, "extension.json").isFile shouldBe true
    }

    private fun showEditorFor(source: FlorisRef) {
        composeRule.setContent {
            Frame {
                PreparedExtensionWorkspace(
                    prepare = {
                        attempts++
                        workDir.deleteRecursively()
                        workDir.mkdirs()
                        unpackExtensionSource(context, source, workDir)
                        "ready"
                    },
                    onBack = { backPresses++ },
                ) { state ->
                    Text("Editor $state")
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertExplains(@StringRes reason: Int) {
        val announced = hasText(string(R.string.ext__editor__preparation_failed_title), substring = true) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        composeRule.onAllNodes(announced).fetchSemanticsNodes().size shouldBe 1
        composeRule.onNodeWithText(string(reason), substring = true).assertExists()
        composeRule.onNodeWithText(string(R.string.action__retry)).assertExists()
        composeRule.onNodeWithText(string(R.string.action__back)).assertExists()

        val shown = composeRule.onAllNodes(SemanticsMatcher("any node") { true } and !isRoot(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            .joinToString("\n")
        Leaks.filter { it in shown } shouldBe emptyList()
        (context.filesDir.absolutePath in shown) shouldBe false
        (context.cacheDir.absolutePath in shown) shouldBe false
    }

    private fun fixtureFile(name: String) = File(File(context.filesDir, FixtureDir), name)

    private fun writeExtensionArchive(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            zip.putNextEntry(ZipEntry("extension.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
    }

    private fun string(@StringRes id: Int) = context.getString(id)

    private companion object {
        const val FixtureDir = "extension-preparation-fixtures"

        /** Pieces of an exception or a stack trace that must never reach the screen. */
        val Leaks = listOf("Exception", "Error:", "\tat ", ".kt:", "java.", "dev.patrickgold", "/data/", ".flex")
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            CompositionLocalProvider(LocalNavController provides rememberNavController()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }
}
