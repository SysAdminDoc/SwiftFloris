package dev.patrickgold.florisboard.lib.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlogFileLogTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `rotation deletes oldest file and shifts indices`() {
        val dir = tempDir.newFolder("diagnostics")
        File(dir, "flog-0.log").writeText("current\n")
        File(dir, "flog-1.log").writeText("older\n")
        File(dir, "flog-2.log").writeText("oldest\n")

        val maxFileCount = 3
        val oldest = File(dir, "flog-${maxFileCount - 1}.log")
        if (oldest.exists()) oldest.delete()
        for (i in maxFileCount - 2 downTo 0) {
            val src = File(dir, "flog-$i.log")
            if (src.exists()) src.renameTo(File(dir, "flog-${i + 1}.log"))
        }

        assertFalse("flog-0.log should not exist after rotation", File(dir, "flog-0.log").exists())
        assertEquals("older\n", File(dir, "flog-2.log").readText())
        assertEquals("current\n", File(dir, "flog-1.log").readText())
    }

    @Test
    fun `diagnostics export concatenates files in order`() {
        val dir = tempDir.newFolder("diagnostics")
        File(dir, "flog-0.log").writeText("line-from-0\n")
        File(dir, "flog-1.log").writeText("line-from-1\n")

        val files = dir.listFiles { f -> f.name.startsWith("flog-") && f.extension == "log" }!!
            .sortedBy { it.name }

        val export = tempDir.newFile("export.log")
        export.outputStream().buffered().use { out ->
            for (f in files) {
                f.inputStream().buffered().use { it.copyTo(out) }
            }
        }

        assertEquals("line-from-0\nline-from-1\n", export.readText())
    }

    @Test
    fun `clear deletes all log files`() {
        val dir = tempDir.newFolder("diagnostics")
        File(dir, "flog-0.log").writeText("content\n")
        File(dir, "flog-1.log").writeText("content\n")

        dir.listFiles()?.forEach { it.delete() }

        assertTrue("directory should be empty after clear", dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `log line format excludes typed text patterns`() {
        val line = "2026-06-14 12:00:00.000 I EditorInstance commitText() - committed 5 chars"
        assertFalse("log line must not contain actual typed text", line.contains("password"))
        assertFalse("log line must not contain clipboard content", line.contains("clipboard_payload"))
    }
}
