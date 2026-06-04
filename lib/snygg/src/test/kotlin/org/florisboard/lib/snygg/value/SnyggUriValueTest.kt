package org.florisboard.lib.snygg.value

import androidx.compose.ui.layout.ContentScale
import org.junit.jupiter.api.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SnyggUriValueTest {
    private val encoder = SnyggUriValue

    @Test
    fun `deserialize uri values`() {
        val pairs = listOf(
            // valid
            "uri(`flex:/my_image.png`)" to SnyggUriValue("flex:/my_image.png"),
            "uri(`flex:/roboto.ttf`)" to SnyggUriValue("flex:/roboto.ttf"),
            // invalid
            "some-color" to null,
        )
        assertAll(pairs.map { (raw, expected) -> {
            assertEquals(expected, encoder.deserialize(raw).getOrNull(), "deserialize $raw")
        } })
    }

    @Test
    fun `serialize uri values`() {
        val pairs = listOf(
            // valid
            SnyggUriValue("flex:/my_image.png") to "uri(`flex:/my_image.png`)",
            SnyggUriValue("flex:/roboto.ttf") to "uri(`flex:/roboto.ttf`)",
            // invalid
            SnyggDefinedVarValue("shenanigans") to null
        )
        assertAll(pairs.map { (snyggValue, expected) -> {
            assertEquals(expected, encoder.serialize(snyggValue).getOrNull(), "serialize $snyggValue")
        } })
    }

    @Test
    fun `check class of default value`() {
        assertIs<SnyggUriValue>(encoder.defaultValue())
    }

    @Test
    fun `deserialize contentScale values`() {
        val pairs = listOf(
            "crop" to SnyggContentScaleValue(ContentScale.Crop),
            "fill-bounds" to SnyggContentScaleValue(ContentScale.FillBounds),
            "fill-height" to SnyggContentScaleValue(ContentScale.FillHeight),
            "fill-width" to SnyggContentScaleValue(ContentScale.FillWidth),
            "fit" to SnyggContentScaleValue(ContentScale.Fit),
            "inside" to SnyggContentScaleValue(ContentScale.Inside),
            "none" to SnyggContentScaleValue(ContentScale.None),
            "unknown" to null,
        )
        assertAll(pairs.map { (raw, expected) -> {
            assertEquals(expected, SnyggContentScaleValue.deserialize(raw).getOrNull(), "deserialize $raw")
        } })
    }

    @Test
    fun `serialize contentScale values`() {
        val pairs = listOf(
            SnyggContentScaleValue(ContentScale.Crop) to "crop",
            SnyggContentScaleValue(ContentScale.FillBounds) to "fill-bounds",
            SnyggContentScaleValue(ContentScale.FillHeight) to "fill-height",
            SnyggContentScaleValue(ContentScale.FillWidth) to "fill-width",
            SnyggContentScaleValue(ContentScale.Fit) to "fit",
            SnyggContentScaleValue(ContentScale.Inside) to "inside",
            SnyggContentScaleValue(ContentScale.None) to "none",
            SnyggUriValue("flex:/my_image.png") to null,
        )
        assertAll(pairs.map { (snyggValue, expected) -> {
            assertEquals(expected, SnyggContentScaleValue.serialize(snyggValue).getOrNull(), "serialize $snyggValue")
        } })
    }

    @Test
    fun `check class and value of default contentScale`() {
        assertEquals(
            SnyggContentScaleValue(ContentScale.FillBounds),
            SnyggContentScaleValue.defaultValue(),
        )
    }
}
