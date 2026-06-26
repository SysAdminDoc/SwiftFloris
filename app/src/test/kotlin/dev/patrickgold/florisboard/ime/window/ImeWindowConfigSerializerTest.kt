/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class ImeWindowConfigSerializerTest : FunSpec({

    test("fixed mode entries exclude the removed THUMBS placeholder") {
        ImeWindowMode.Fixed.entries.map { it.name } shouldBe listOf("NORMAL", "COMPACT", "SPLIT")
    }

    test("legacy THUMBS window config deserializes as NORMAL and does not reserialize") {
        val type = ImeFormFactor.Type.PHONE_PORTRAIT
        val current = mapOf(
            type to ImeWindowConfig(
                mode = ImeWindowMode.FIXED,
                fixedMode = ImeWindowMode.Fixed.NORMAL,
                fixedProps = mapOf(
                    ImeWindowMode.Fixed.NORMAL to ImeWindowProps.Fixed(
                        keyboardHeight = 123.dp,
                        paddingLeft = 4.dp,
                        paddingRight = 8.dp,
                        paddingBottom = 12.dp,
                    ),
                ),
                floatingMode = ImeWindowMode.Floating.NORMAL,
                floatingProps = mapOf(
                    ImeWindowMode.Floating.NORMAL to ImeWindowProps.Floating(
                        keyboardHeight = 100.dp,
                        keyboardWidth = 220.dp,
                        offsetLeft = 24.dp,
                        offsetBottom = 16.dp,
                    ),
                ),
            ),
        )
        val legacyJson = ImeWindowConfig.ByTypeSerializer
            .serialize(current)
            .replace("\"NORMAL\"", "\"THUMBS\"")

        val decoded = ImeWindowConfig.ByTypeSerializer.deserialize(legacyJson)
        val config = decoded.getValue(type)

        config.fixedMode shouldBe ImeWindowMode.Fixed.NORMAL
        config.fixedProps.keys.single() shouldBe ImeWindowMode.Fixed.NORMAL
        config.fixedProps.getValue(ImeWindowMode.Fixed.NORMAL).keyboardHeight shouldBe 123.dp
        config.floatingMode shouldBe ImeWindowMode.Floating.NORMAL
        config.floatingProps.keys.single() shouldBe ImeWindowMode.Floating.NORMAL
        ImeWindowConfig.ByTypeSerializer.serialize(decoded) shouldNotContain "THUMBS"
    }
})
