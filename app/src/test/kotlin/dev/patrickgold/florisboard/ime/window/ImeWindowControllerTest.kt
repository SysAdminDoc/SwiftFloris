/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first

private fun rootInsets(widthDp: Int, heightDp: Int): ImeInsets.Root {
    val boundsDp = DpRect(0.dp, 0.dp, widthDp.dp, heightDp.dp)
    return ImeInsets.Root(
        boundsDp = boundsDp,
        boundsPx = IntRect(0, 0, widthDp, heightDp),
        formFactor = ImeFormFactor.of(boundsDp),
    )
}

class ImeWindowControllerTest : FunSpec({
    val tolerance = 1e-3f.dp

    coroutineTestScope = true

    context("isWindowShown state") {
        test("simple onShown onHidden") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.isWindowShown.value shouldBe false
            windowController.onWindowShown() shouldBe true
            windowController.isWindowShown.value shouldBe true
            windowController.onWindowHidden() shouldBe true
            windowController.isWindowShown.value shouldBe false
        }

        test("duplicate onWindowShown is detected") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.isWindowShown.value shouldBe false
            windowController.onWindowShown() shouldBe true
            windowController.isWindowShown.value shouldBe true
            windowController.onWindowShown().shouldBe(false, "duplicate onWindowShown is detected")
            windowController.isWindowShown.value shouldBe true
        }

        test("duplicate onWindowHidden is detected") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.isWindowShown.value shouldBe false
            windowController.onWindowShown() shouldBe true
            windowController.isWindowShown.value shouldBe true
            windowController.onWindowHidden() shouldBe true
            windowController.isWindowShown.value shouldBe false
            windowController.onWindowHidden().shouldBe(false, "duplicate onWindowHidden should fail")
            windowController.isWindowShown.value shouldBe false
        }
    }

    context("floating onboarding") {
        test("preference round-trips through datastore") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)

            prefs.keyboard.floatingOnboardingShown.get() shouldBe false
            prefs.keyboard.floatingOnboardingShown.set(true)
            prefs.keyboard.floatingOnboardingShown.get() shouldBe true
            prefs.keyboard.floatingOnboardingShown.set(false)
            prefs.keyboard.floatingOnboardingShown.get() shouldBe false
        }

        test("visibility predicate only allows first floating entry") {
            FloatingModeOnboarding.AUTO_DISMISS_MILLIS shouldBe 4_000L
            FloatingModeOnboarding.shouldShow(ImeWindowMode.FLOATING, alreadyShown = false) shouldBe true
            FloatingModeOnboarding.shouldShow(ImeWindowMode.FIXED, alreadyShown = false) shouldBe false
            FloatingModeOnboarding.shouldShow(ImeWindowMode.FLOATING, alreadyShown = true) shouldBe false
        }

        test("onWindowShown applies floating default and queues onboarding once") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            prefs.keyboard.startInFloatingMode.set(true)
            val windowController = ImeWindowController(prefs, backgroundScope)

            windowController.onWindowShown() shouldBe true

            windowController.activeWindowSpec.first { it is ImeWindowSpec.Floating }
                .shouldBeInstanceOf<ImeWindowSpec.Floating>()
            windowController.floatingOnboardingVisible.first { it } shouldBe true
            prefs.keyboard.floatingOnboardingShown.get() shouldBe true

            windowController.dismissFloatingOnboarding()
            windowController.floatingOnboardingVisible.value shouldBe false
            windowController.onWindowHidden() shouldBe true
            windowController.onWindowShown() shouldBe true
            windowController.activeWindowSpec.first { it is ImeWindowSpec.Floating }
            windowController.floatingOnboardingVisible.value shouldBe false
        }
    }

    context("split keyboard viability") {
        test("stored split mode falls back to fixed normal on narrow roots") {
            val rootInsets = rootInsets(widthDp = 500, heightDp = 800)
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            prefs.keyboard.windowConfig.set(
                mapOf(
                    rootInsets.formFactor.typeGuess to ImeWindowConfig(
                        mode = ImeWindowMode.FIXED,
                        fixedMode = ImeWindowMode.Fixed.SPLIT,
                    ),
                ),
            )
            val windowController = ImeWindowController(prefs, backgroundScope)
            windowController.updateRootInsets(rootInsets)

            val spec = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                .shouldBeInstanceOf<ImeWindowSpec.Fixed>()

            spec.fixedMode shouldBe ImeWindowMode.Fixed.NORMAL
        }

        test("split preference promotes fixed mode only on viable roots") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            prefs.keyboard.splitKeyboardEnabled.set(true)
            val windowController = ImeWindowController(prefs, backgroundScope)
            windowController.updateRootInsets(rootInsets(widthDp = 800, heightDp = 700))
            windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }

            windowController.onWindowShown() shouldBe true

            val spec = windowController.activeWindowSpec.first {
                it is ImeWindowSpec.Fixed && it.fixedMode == ImeWindowMode.Fixed.SPLIT
            }.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
            spec.constraints.shouldBeInstanceOf<ImeWindowConstraints.Fixed.Split>()
        }

        test("split preference stays fixed normal on non-viable roots") {
            val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
            prefs.keyboard.splitKeyboardEnabled.set(true)
            val windowController = ImeWindowController(prefs, backgroundScope)
            windowController.updateRootInsets(rootInsets(widthDp = 500, heightDp = 800))
            val initialSpec = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
                .shouldBeInstanceOf<ImeWindowSpec.Fixed>()

            windowController.onWindowShown() shouldBe true

            initialSpec.fixedMode shouldBe ImeWindowMode.Fixed.NORMAL
            windowController.activeWindowSpec.value
                .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                .fixedMode shouldBe ImeWindowMode.Fixed.NORMAL
        }
    }

    context("for all root insets") {
        test("for all fixed window configs in prefs") {
            checkAll(
                Arb.rootInsets(),
                Arb.windowConfigFixed(),
            ) { rootInsets, windowConfig ->
                val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
                prefs.keyboard.windowConfig.set(mapOf(rootInsets.formFactor.typeGuess to windowConfig))
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)

                val spec = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }

                assertSoftly {
                    val effectiveFixedMode = if (
                        windowConfig.fixedMode == ImeWindowMode.Fixed.SPLIT &&
                        !(ImeWindowConstraints.of(
                            rootInsets,
                            ImeWindowMode.Fixed.SPLIT,
                        ) as ImeWindowConstraints.Fixed.Split).isViable
                    ) {
                        ImeWindowMode.Fixed.NORMAL
                    } else {
                        windowConfig.fixedMode
                    }
                    val constraints = ImeWindowConstraints.of(rootInsets, effectiveFixedMode)
                    val spec = spec.shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                    spec.fixedMode shouldBe effectiveFixedMode
                    spec.props.shouldBeConstrainedTo(constraints, tolerance)
                }
            }
        }

        test("for all floating window configs in prefs") {
            checkAll(
                Arb.rootInsets(),
                Arb.windowConfigFloating(),
            ) { rootInsets, windowConfig ->
                val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
                prefs.keyboard.windowConfig.set(mapOf(rootInsets.formFactor.typeGuess to windowConfig))
                val windowController = ImeWindowController(prefs, backgroundScope)
                windowController.updateRootInsets(rootInsets)

                val spec = windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }

                assertSoftly {
                    val constraints = ImeWindowConstraints.of(rootInsets, windowConfig.floatingMode)
                    val spec = spec.shouldBeInstanceOf<ImeWindowSpec.Floating>()
                    spec.props.shouldBeConstrainedTo(constraints, tolerance)
                }
            }
        }
    }
})
