package dev.patrickgold.florisboard.ime.media

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import kotlin.test.Test

class MediaPaletteAccessibilityContractTest {
    @Test
    fun `emoji palette exposes search, tabs, variations, and pinned group actions`() {
        val palette = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/EmojiPaletteView.kt",
        ).readText()
        val categories = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/EmojiCategory.kt",
        ).readText()
        val chips = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/PinnedGroupsPaletteRow.kt",
        ).readText()
        val strings = locateProjectFile("app/src/main/res/values/strings.xml").readText()

        palette shouldContain "emoji__search__field_content_description"
        palette shouldContain "emoji__search__clear"
        palette shouldContain "category.labelRes()"
        palette shouldContain "emoji__variation__select_a11y"
        palette shouldContain "onClick(label = variationLabel)"
        palette shouldContain "EmojiPaletteStateText"
        palette shouldContain "MediaEmojiSubheader.elementName"
        palette shouldNotContain "import androidx.compose.material3.Text"
        categories shouldContain "fun labelRes()"
        chips shouldContain "emoji__pin_group__chip_a11y"
        chips shouldContain "onLongClick(label = chipHintLabel)"
        strings shouldContain "emoji__search__field_content_description"
        strings shouldContain "emoji__pin_group__chip_hint_a11y"
    }

    @Test
    fun `sticker palette exposes search and disabled tile semantics`() {
        val palette = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt",
        ).readText()
        val strings = locateProjectFile("app/src/main/res/values/strings.xml").readText()

        palette shouldContain "sticker__search__field_content_description"
        palette shouldContain "sticker__search__clear"
        palette shouldContain "sticker__tile_disabled_a11y"
        palette shouldContain "disabled()"
        palette shouldContain "onClick(label = tileLabel)"
        palette shouldContain "sticker__search__empty"
        palette shouldContain "MediaEmojiSubheader.elementName"
        strings shouldContain "sticker__tile_disabled_a11y"
    }

    @Test
    fun `pin to group sheet exposes field, existing group, and live error semantics`() {
        val sheet = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/PinToGroupSheet.kt",
        ).readText()
        val strings = locateProjectFile("app/src/main/res/values/strings.xml").readText()

        sheet shouldContain "emoji__pin_group__field_content_description"
        sheet shouldContain "emoji__pin_group__existing_group_a11y"
        sheet shouldContain "liveRegion = LiveRegionMode.Assertive"
        sheet shouldContain "onClick(label = actionLabel)"
        strings shouldContain "emoji__pin_group__field_content_description"
        strings shouldContain "emoji__pin_group__existing_group_a11y"
    }
}

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.isFile }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
