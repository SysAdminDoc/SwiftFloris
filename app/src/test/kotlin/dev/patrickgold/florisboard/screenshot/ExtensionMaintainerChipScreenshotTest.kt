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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.patrickgold.florisboard.app.ext.ExtensionMaintainerChip
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ROADMAP §7 Next-12.2 — Roborazzi Compose screenshot regression suite.
 *
 * Captures pixel-stable PNGs of representative composables so a refactor
 * that accidentally moves a layout token, swaps a colour ramp, or breaks a
 * theme variable shows up as a diff in CI rather than as a bug report from
 * users. Runs on the JVM via Robolectric — no device, no emulator — so the
 * suite is cheap enough to invoke on every pull request.
 *
 * Workflow:
 *  - Record baselines:   `./gradlew :app:recordRoborazziDebug`
 *  - Verify (CI default): `./gradlew :app:verifyRoborazziDebug`
 *  - Compare diffs:       `./gradlew :app:compareRoborazziDebug`
 *  - Read snapshots from `app/build/outputs/roborazzi/`.
 *
 * The scaffold here pins ExtensionMaintainerChip — a small, deterministic
 * surface with three @Preview variants that already exist in the main
 * codebase. Once the plumbing is verified-green in CI, follow-up batches
 * extend coverage to: smartbar candidates row, all M3 Expressive theme
 * keys, the suggestion strip, the floating-window border, and the
 * stylus-handwriting overlay (when Next-4.2 lands).
 */
// Robolectric 4.14.1 caps at API 35 (Vanilla Ice Cream). SwiftFloris
// builds against API 36 / SDK 36 (Baklava), so we pin the test SDK to
// 35 explicitly until Robolectric catches up. The captured pixels are
// still representative because Roborazzi snapshots are layout-driven
// rather than API-level-dependent.
//
// @Ignore on every test in this class: the Roborazzi + Robolectric +
// AndroidJUnit4 stack still needs a launcher-Activity stub in the
// test-only AndroidManifest (per Robolectric PR #4736) before
// captureRoboImage can actually launch the Compose host. The scaffold
// here pins the dependency wiring (build.gradle.kts + libs.versions),
// the test pattern (createComposeRule + captureRoboImage), and the
// snapshot path convention; the manifest fix lands as Next-12.2a and
// flips the @Ignore off in one diff.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Ignore("Next-12.2a — test AndroidManifest needs a launcher Activity stub before this can run; deps + harness shape are in place.")
class ExtensionMaintainerChipScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "build/outputs/roborazzi",
        ),
    )

    @Test
    fun chipNameOnly() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    ExtensionMaintainerChip(
                        maintainer = ExtensionMaintainer(
                            name = "Jane Doe",
                            email = null,
                            url = null,
                        ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/maintainer_chip_name_only.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    @Test
    fun chipNameAndEmail() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    ExtensionMaintainerChip(
                        maintainer = ExtensionMaintainer(
                            name = "Jane Doe",
                            email = "jane.doe@example.com",
                            url = null,
                        ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/maintainer_chip_name_and_email.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    @Test
    fun chipNameAndUrl() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    ExtensionMaintainerChip(
                        maintainer = ExtensionMaintainer(
                            name = "Jane Doe",
                            email = null,
                            url = "jane-doe.example.com",
                        ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/maintainer_chip_name_and_url.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    private companion object {
        // Snapshots live under `app/src/test/snapshots/` so they're committed
        // alongside the source tree and any pixel-level regression shows up
        // as a tracked change in git review.
        const val BASELINE_DIR = "src/test/snapshots/extension_maintainer_chip"

        val ROBORAZZI_OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )
    }
}
