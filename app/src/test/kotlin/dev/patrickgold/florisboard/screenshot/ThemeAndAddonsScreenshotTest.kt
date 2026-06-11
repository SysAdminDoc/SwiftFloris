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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import dev.patrickgold.florisboard.app.settings.addons.AddonsSettingsScreen
import dev.patrickgold.florisboard.ime.addon.AddonManifest
import dev.patrickgold.florisboard.ime.addon.AddonRegistry
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStore
import dev.patrickgold.florisboard.ime.addon.AddonType
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import java.io.File
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ROADMAP F11 - Roborazzi baselines for the bundled theme and addon
 * surfaces that previously kept visual CI advisory-only.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeAndAddonsScreenshotTest {
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
        AddonRegistryStore.reset()
    }

    @Test
    fun swiftkeyHighContrastKeyboardSurface() {
        composeRule.setContent {
            KeyboardThemeSurface(stylesheetFileName = "swiftkey_high_contrast.json")
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/swiftkey_high_contrast_keyboard_surface.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    @Test
    fun auroraAnimatedKeyboardSurface() {
        composeRule.setContent {
            KeyboardThemeSurface(stylesheetFileName = "aurora_animated.json")
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/aurora_animated_keyboard_surface.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    @Test
    fun addonsSettingsRegistrySurface() {
        seedAddonRegistry()

        composeRule.setContent {
            ProvideLocalizedResources(
                resourcesContext = composeRule.activity,
                appName = R.string.app_name,
            ) {
                FlorisAppTheme(theme = AppTheme.LIGHT) {
                    val navController = rememberNavController()
                    CompositionLocalProvider(LocalNavController provides navController) {
                        ProvideDefaultDialogPrefStrings(
                            confirmLabel = stringRes(R.string.action__ok),
                            dismissLabel = stringRes(R.string.action__cancel),
                            neutralLabel = stringRes(R.string.action__default),
                        ) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                                    AddonsSettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/addons_settings_registry_surface.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    private fun waitForPreferenceStore() {
        val app = composeRule.activity.application as FlorisApplication
        composeRule.waitUntil(timeoutMillis = 10_000) {
            app.preferenceStoreLoaded.value
        }
    }

    private fun seedAddonRegistry() {
        val registry = AddonRegistry(
            initialPinnedSigningCertificates = mapOf(
                "dev.swiftfloris.addons.polish" to fingerprint("AA"),
                "dev.swiftfloris.addons.nord" to fingerprint("BB"),
                "dev.swiftfloris.addons.rejected" to fingerprint("CD"),
            ),
        )
        registry.refresh(
            listOf(
                addonManifest(
                    packageName = "dev.swiftfloris.addons.polish",
                    type = AddonType.DICTIONARY_PACK,
                    displayName = "Polish Dictionary Pack",
                    version = 3,
                    fingerprint = fingerprint("AA"),
                    bundleSizeBytes = 2_359_296,
                ),
                addonManifest(
                    packageName = "dev.swiftfloris.addons.nord",
                    type = AddonType.THEME_PACK,
                    displayName = "Nord Theme Pack",
                    version = 8,
                    fingerprint = fingerprint("BB"),
                    bundleSizeBytes = 311_296,
                ),
                addonManifest(
                    packageName = "dev.swiftfloris.addons.rejected",
                    type = AddonType.LANGUAGE_PACK,
                    displayName = "Changed Certificate Pack",
                    version = 2,
                    fingerprint = fingerprint("DD"),
                    bundleSizeBytes = 98_304,
                ),
            ),
        )
        AddonRegistryStore.setActive(registry)
    }

    private fun addonManifest(
        packageName: String,
        type: AddonType,
        displayName: String,
        version: Long,
        fingerprint: String,
        bundleSizeBytes: Long,
    ) = AddonManifest(
        packageName = packageName,
        type = type,
        version = version,
        displayName = displayName,
        descriptorResourceId = 0,
        licenseSpdxId = "Apache-2.0",
        signingCertSha256 = fingerprint,
        bundleSizeBytes = bundleSizeBytes,
    )

    private companion object {
        const val BASELINE_DIR = "src/test/snapshots/theme_and_addons"

        val ROBORAZZI_OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )

        fun fingerprint(byte: String): String =
            List(32) { byte }.joinToString(separator = ":")
    }
}

@Composable
private fun KeyboardThemeSurface(stylesheetFileName: String) {
    val stylesheet = remember(stylesheetFileName) {
        loadBundledStylesheet(stylesheetFileName)
    }
    val theme = rememberSnyggTheme(stylesheet)

    MaterialTheme {
        ProvideSnyggTheme(theme) {
            SnyggBox(
                elementName = FlorisImeUi.Window.elementName,
                attributes = mapOf(FlorisImeUi.Attr.WindowMode to "fixed"),
                modifier = Modifier.size(width = 360.dp, height = 286.dp),
            ) {
                SnyggColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SnyggRow(
                        elementName = FlorisImeUi.Smartbar.elementName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CandidateChip("privacy", weight = 1.0f)
                        CandidateChip("offline", weight = 1.0f)
                        CandidateChip("SwiftFloris", weight = 1.3f)
                    }
                    KeyRow(
                        listOf(
                            KeySpec("Q", code = 81),
                            KeySpec("W", code = 87, selector = SnyggSelector.PRESSED),
                            KeySpec("E", code = 69),
                            KeySpec("R", code = 82),
                            KeySpec("T", code = 84),
                            KeySpec("Y", code = 89),
                            KeySpec("U", code = 85),
                            KeySpec("I", code = 73),
                        ),
                    )
                    KeyRow(
                        listOf(
                            KeySpec("A", code = 65),
                            KeySpec("S", code = 83),
                            KeySpec("D", code = 68),
                            KeySpec("F", code = 70),
                            KeySpec("G", code = 71),
                            KeySpec("H", code = 72),
                            KeySpec("J", code = 74),
                        ),
                    )
                    KeyRow(
                        listOf(
                            KeySpec("CAPS", code = -11, weight = 1.15f, shiftState = "caps_lock"),
                            KeySpec("Z", code = 90),
                            KeySpec("X", code = 88),
                            KeySpec("C", code = 67),
                            KeySpec("V", code = 86),
                            KeySpec("go", code = 10, weight = 1.3f),
                        ),
                    )
                    SnyggRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ThemeKey(KeySpec("123", code = -201, weight = 1.0f))
                        ThemeKey(KeySpec("space", code = 32, weight = 3.1f))
                        ThemeKey(KeySpec(".", code = 46, weight = 1.0f))
                    }
                    PopupPreview()
                }
            }
        }
    }
}

@Composable
private fun KeyRow(keys: List<KeySpec>) {
    SnyggRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(39.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        for (key in keys) {
            ThemeKey(key)
        }
    }
}

@Composable
private fun RowScope.CandidateChip(text: String, weight: Float) {
    SnyggBox(
        elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(text = text)
    }
}

@Composable
private fun RowScope.ThemeKey(key: KeySpec) {
    SnyggBox(
        elementName = FlorisImeUi.Key.elementName,
        attributes = key.attributes,
        selector = key.selector,
        modifier = Modifier
            .weight(key.weight)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(text = key.label, modifier = Modifier.align(Alignment.Center))
        if (key.label.length == 1) {
            SnyggText(
                elementName = FlorisImeUi.KeyHint.elementName,
                text = key.label.lowercase(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.PopupPreview() {
    SnyggBox(
        elementName = FlorisImeUi.KeyPopupBox.elementName,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(164.dp)
            .height(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        SnyggRow(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopupElement("e", code = 101)
            PopupElement("e'", code = 233, selector = SnyggSelector.FOCUS)
            PopupElement("e^", code = 234)
            PopupElement("...", code = -255)
        }
    }
}

@Composable
private fun RowScope.PopupElement(label: String, code: Int, selector: SnyggSelector? = null) {
    SnyggBox(
        elementName = FlorisImeUi.KeyPopupElement.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to code),
        selector = selector,
        modifier = Modifier
            .weight(1.0f)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(text = label)
    }
}

private data class KeySpec(
    val label: String,
    val code: Int,
    val weight: Float = 1.0f,
    val selector: SnyggSelector? = null,
    val shiftState: String? = null,
) {
    val attributes: Map<String, Any> = buildMap {
        put(FlorisImeUi.Attr.Code, code)
        if (shiftState != null) {
            put(FlorisImeUi.Attr.ShiftState, shiftState)
        }
    }
}

private fun loadBundledStylesheet(fileName: String): SnyggStylesheet {
    val candidates = listOf(
        File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
        File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
    )
    val file = candidates.firstOrNull { it.exists() }
        ?: error("$fileName not reachable from working directory ${File(".").absolutePath}")
    return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
}
