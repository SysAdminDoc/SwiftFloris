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

package dev.patrickgold.florisboard.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.app.settings.HomeScreen
import dev.patrickgold.florisboard.app.settings.about.AiFeaturesScreen
import dev.patrickgold.florisboard.app.settings.about.ThirdPartyLicensesScreen
import dev.patrickgold.florisboard.app.settings.advanced.BackupScreen
import dev.patrickgold.florisboard.app.settings.advanced.RestoreScreen
import dev.patrickgold.florisboard.app.settings.keyboard.CustomLayoutEditorPreviewSurface
import dev.patrickgold.florisboard.app.settings.mcp.McpSettingsScreen
import dev.patrickgold.florisboard.app.settings.privacy.PrivacyAuditScreen
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchScreen
import dev.patrickgold.florisboard.app.settings.sync.SyncSettingsScreen
import dev.patrickgold.florisboard.app.settings.typing.SnippetSettingsScreen
import dev.patrickgold.florisboard.app.settings.typing.TypingStatsScreen
import dev.patrickgold.florisboard.app.settings.voice.VoiceInputScreen
import dev.patrickgold.florisboard.app.setup.SetupScreen
import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import dev.patrickgold.florisboard.ime.mcp.DaemonEntry
import dev.patrickgold.florisboard.ime.mcp.DaemonKey
import dev.patrickgold.florisboard.ime.mcp.McpBridgeContract
import dev.patrickgold.florisboard.ime.mcp.McpDaemonRegistry
import dev.patrickgold.florisboard.ime.mcp.McpToolDescriptor
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F40 screen-level Roborazzi targets for settings surfaces that are otherwise
 * easy to regress without touching policy-level JVM tests. `AddonsSettingsScreen`
 * already has an active registry snapshot in [ThemeAndAddonsScreenshotTest];
 * this class covers the remaining high-value settings screens.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PendingSettingsScreensScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "build/outputs/roborazzi",
        ),
    )

    @Before
    fun setUp() {
        waitForPreferenceStore()
    }

    @After
    fun tearDown() {
        McpDaemonRegistry.setActive(emptyMap())
        AddonInvocationAudit.clear()
    }

    @Test
    fun homeScreenOverview() {
        captureSettingsScreen("home_screen_overview.png") {
            HomeScreen()
        }
    }

    @Test
    fun setupScreen() {
        captureSettingsScreen("setup_screen.png", theme = AppTheme.DARK) {
            SetupScreen()
        }
    }

    @Test
    fun settingsSearchScreen() {
        captureSettingsScreen("settings_search_screen.png") {
            SettingsSearchScreen()
        }
    }

    @Test
    fun mcpSettingsScreen() {
        seedMcpDaemons()
        captureSettingsScreen("mcp_settings_screen.png") {
            McpSettingsScreen()
        }
    }

    @Test
    fun typingStatsScreen() {
        captureSettingsScreen("typing_stats_screen.png") {
            TypingStatsScreen()
        }
    }

    @Test
    fun voiceInputScreen() {
        captureSettingsScreen("voice_input_screen.png") {
            VoiceInputScreen()
        }
    }

    @Test
    fun aiFeaturesScreen() {
        captureSettingsScreen("ai_features_screen.png") {
            AiFeaturesScreen()
        }
    }

    @Test
    fun thirdPartyLicensesScreenRenders() {
        val title = composeRule.activity.getString(R.string.about__third_party_licenses__title)
        composeRule.setContent {
            SettingsScreenshotFrame(theme = AppTheme.LIGHT) {
                ThirdPartyLicensesScreen()
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(title))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun customLayoutEditorScreen() {
        captureSettingsScreen("custom_layout_editor_screen.png", theme = AppTheme.DARK) {
            CustomLayoutEditorPreviewSurface()
        }
    }

    @Test
    fun snippetSettingsScreen() {
        captureSettingsScreen("snippet_settings_screen.png") {
            SnippetSettingsScreen()
        }
    }

    @Test
    fun privacyAuditLogScreen() {
        seedPrivacyAudit()
        captureSettingsScreen("privacy_audit_log_screen.png", theme = AppTheme.AMOLED_DARK) {
            PrivacyAuditScreen()
        }
    }

    @Test
    fun syncSettingsScreen() {
        captureSettingsScreen("sync_settings_screen.png", theme = AppTheme.DARK) {
            SyncSettingsScreen()
        }
    }

    @Test
    fun backupScreen() {
        captureSettingsScreen("backup_screen.png") {
            BackupScreen()
        }
    }

    @Test
    fun restoreScreen() {
        captureSettingsScreen("restore_screen.png") {
            RestoreScreen()
        }
    }

    private fun captureSettingsScreen(
        fileName: String,
        theme: AppTheme = AppTheme.LIGHT,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            SettingsScreenshotFrame(theme = theme, content = content)
        }
        composeRule.waitForIdle()
        if (fileName == "typing_stats_screen.png") {
            waitForTypingStats()
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/$fileName",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    private fun seedPrivacyAudit() {
        AddonInvocationAudit.clear()
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.MCP,
            outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            subject = "dev.swiftfloris.mcp.calendar::calendar.next_event",
            timestampMillis = 1_720_000_000_000L,
        )
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.TRANSLATION,
            outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
            reason = "sensitive field",
            subject = "en->de",
            timestampMillis = 1_720_000_060_000L,
        )
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
            outcome = AddonInvocationAudit.Outcome.FAILED,
            reason = "no installed provider",
            subject = "com.example.notes",
            timestampMillis = 1_720_000_120_000L,
        )
    }

    private fun waitForPreferenceStore() {
        val app = composeRule.activity.application as FlorisApplication
        composeRule.waitUntil(timeoutMillis = 10_000) {
            app.preferenceStoreLoaded.value
        }
    }

    private fun waitForTypingStats() {
        val loadingText = composeRule.activity.getString(R.string.settings__typing_stats__loading)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(loadingText))
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun seedMcpDaemons() {
        val calendarKey = DaemonKey(
            packageName = "dev.swiftfloris.mcp.calendar",
            daemonClassName = ".CalendarDaemonService",
        )
        val notesKey = DaemonKey(
            packageName = "dev.swiftfloris.mcp.notes",
            daemonClassName = ".NotesDaemonService",
        )
        McpDaemonRegistry.setActive(
            mapOf(
                calendarKey to DaemonEntry(
                    key = calendarKey,
                    protocolVersion = McpBridgeContract.SUPPORTED_PROTOCOL_VERSION,
                    tools = listOf(
                        McpToolDescriptor(
                            name = "calendar.next_event",
                            description = "Insert the next local calendar event.",
                            parameterSchemaJson = "{}",
                        ),
                        McpToolDescriptor(
                            name = "calendar.create_task",
                            description = "Create a local task from selected text.",
                            parameterSchemaJson = "{}",
                        ),
                    ),
                ),
                notesKey to DaemonEntry(
                    key = notesKey,
                    protocolVersion = McpBridgeContract.SUPPORTED_PROTOCOL_VERSION,
                    tools = listOf(
                        McpToolDescriptor(
                            name = "notes.append",
                            description = "Append text to an on-device notes app.",
                            parameterSchemaJson = "{}",
                        ),
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val BASELINE_DIR = "src/test/snapshots/pending_settings_screens"

        val ROBORAZZI_OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )
    }
}

@Composable
private fun SettingsScreenshotFrame(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    ProvideLocalizedResources(
        resourcesContext = androidx.compose.ui.platform.LocalContext.current,
        appName = R.string.app_name,
    ) {
        FlorisAppTheme(theme = theme) {
            val navController = rememberNavController()
            CompositionLocalProvider(LocalNavController provides navController) {
                ProvideDefaultDialogPrefStrings(
                    confirmLabel = stringRes(R.string.action__ok),
                    dismissLabel = stringRes(R.string.action__cancel),
                    neutralLabel = stringRes(R.string.action__default),
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
