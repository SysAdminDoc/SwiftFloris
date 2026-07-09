package dev.patrickgold.florisboard.lib.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToastUsageContractTest {

    @Test
    fun `production app code does not call blocking sync toast helpers`() {
        val sourceRoot = locateProjectDir("app/src/main/kotlin")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { source ->
                val text = source.readText()
                text.contains("showShortToastSync(") || text.contains("showLongToastSync(")
            }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Synchronous IME feedback must use non-blocking postShortToast/showShortToast paths: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `clipboard failure toasts are resource backed`() {
        val editorSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt",
        ).readText()
        val clipboardSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt",
        ).readText()
        val strings = locateProjectFile("app/src/main/res/values/strings.xml").readText()

        assertFalse(editorSource.contains("Failed to retrieve selected text requested to cut"))
        assertFalse(editorSource.contains("Failed to retrieve selected text requested to copy"))
        assertFalse(editorSource.contains("Failed to paste item."))
        assertFalse(clipboardSource.contains("Failed to paste item."))

        for (id in listOf(
            "clipboard__cut_selection_failed",
            "clipboard__copy_selection_failed",
            "clipboard__paste_failed",
        )) {
            assertTrue(editorSource.contains("R.string.$id") || clipboardSource.contains("R.string.$id"))
            assertTrue(strings.contains("name=\"$id\""))
        }
    }

    private fun locateProjectDir(path: String): File {
        return listOf(File(path), File("..", path))
            .firstOrNull { it.isDirectory }
            ?: error("$path not reachable from working directory ${File(".").absolutePath}")
    }

    private fun locateProjectFile(path: String): File {
        return listOf(File(path), File("..", path))
            .firstOrNull { it.isFile }
            ?: error("$path not reachable from working directory ${File(".").absolutePath}")
    }
}
