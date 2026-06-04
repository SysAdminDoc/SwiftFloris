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

import androidx.activity.ComponentActivity

/**
 * ROADMAP §7 Next-12.2a — Roborazzi test host activity.
 *
 * Robolectric's `createComposeRule()` launches a real Activity behind
 * the scenes to host the Compose tree under test. The IME's
 * production AndroidManifest declares only `FlorisImeService` and a
 * `FlorisAppActivity`; neither is appropriate for the screenshot
 * harness (the IME service has no Activity surface; the settings
 * Activity carries the full nav graph). This shim exists purely so
 * the screenshot tests can launch an empty `ComponentActivity` host.
 *
 * Declared only under the **debug** and **releaseRoborazzi** manifest overlays,
 * so it never ships in release builds and never widens the production attack
 * surface. Set `exported=false` so no external app can launch it even on debug.
 */
class RoborazziHostActivity : ComponentActivity()
