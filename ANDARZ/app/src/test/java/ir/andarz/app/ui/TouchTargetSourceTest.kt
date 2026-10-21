package ir.andarz.app.ui

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * WCAG 2.2 SC 2.5.8 / Android 48 dp guidance (U-01, U-02), checked on the sources so it also runs in the
 * release unit-test variant: no IconButton is shrunk below 48 dp and every bare `clickable` names its role
 * for TalkBack. A screenshot-based check stays on the device list (MANUAL_TEST.md).
 */
class TouchTargetSourceTest {
    private val sources: List<File> by lazy {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun iconButtonsAreAtLeast48dp() {
        val small = Regex("""IconButton\([^)]*Modifier\.size\((\d+)\.dp\)""")
        val bad = sources.flatMap { f ->
            small.findAll(f.readText()).filter { it.groupValues[1].toInt() < 48 }.map { f.name + ": " + it.value.take(80) }.toList()
        }
        assertTrue(bad.isEmpty(), "IconButton below 48 dp: $bad")
    }

    @Test
    fun everyClickableDeclaresARole() {
        val bare = Regex("""\.clickable(\s*\{|\(onClick)""")
        val bad = sources.flatMap { f -> f.readLines().withIndex().filter { bare.containsMatchIn(it.value) }.map { f.name + ":" + (it.index + 1) } }
        assertTrue(bad.isEmpty(), "clickable without role: $bad")
    }
}
