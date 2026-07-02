package io.github.j4sp3rtm.todomanager

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color

/**
 * Validates how [TodoHighlightPainter] colorizes the parts of a TODO comment across every comment
 * style: that the keyword, tag, priority, description, and delimiters each get their configured
 * color, and that the description color stops before a closing delimiter (no bleed onto the "back"
 * of a block comment).
 *
 * The painter writes into the editor markup model directly (rather than via an annotator), so this
 * also covers the case the daemon would skip — files the IDE does not run code-insight on.
 *
 * Fixtures use the keyword HACK, which the IDE does not highlight on its own, so any color we read
 * back comes from the painter.
 */
class TodoCommentHighlightingTest : BasePlatformTestCase() {

    private val keywordColor get() = Config.keywordColor("HACK")
    private val descriptionColor get() = Config.descriptionColor()
    private val delimiterColor get() = Config.delimiterColor()

    fun testJavaLineComment() {
        val text = "class S {\n  // HACK fix this\n}"
        paint("S.java", text)

        assertForeground(text, "HACK", keywordColor)
        assertForeground(text, "fix this", descriptionColor)
        // Opening line delimiter "//" is colored; there is no closing delimiter.
        assertForeground(text, "//", delimiterColor)
    }

    fun testJavaBlockComment() {
        val text = "class S {\n  /* HACK fix this */\n}"
        paint("S.java", text)

        assertForeground(text, "HACK", keywordColor)
        assertForeground(text, "fix this", descriptionColor)
        assertForeground(text, "/*", delimiterColor)
        // The closing "*/" is a delimiter, NOT description text — this is the bleed fix.
        assertForegroundAt(closingOffset(text, "*/"), delimiterColor)
        assertNotForegroundAt(closingOffset(text, "*/"), descriptionColor)
    }

    fun testJavaDocComment() {
        val text = "class S {\n  /** HACK fix this */\n  void m() {}\n}"
        paint("S.java", text)

        assertForeground(text, "HACK", keywordColor)
        assertForeground(text, "fix this", descriptionColor)
        assertForeground(text, "/*", delimiterColor)
        assertForegroundAt(closingOffset(text, "*/"), delimiterColor)
        assertNotForegroundAt(closingOffset(text, "*/"), descriptionColor)
    }

    fun testXmlBlockComment() {
        val text = "<root>\n  <!-- HACK fix this -->\n</root>"
        paint("sample.xml", text)

        assertForeground(text, "HACK", keywordColor)
        assertForeground(text, "fix this", descriptionColor)
        assertForeground(text, "<!--", delimiterColor)
        assertForegroundAt(closingOffset(text, "-->"), delimiterColor)
        assertNotForegroundAt(closingOffset(text, "-->"), descriptionColor)
    }

    fun testStructuredTagAndPriority() {
        val text = "class S {\n  // HACK [auth] (high) refresh token\n}"
        paint("S.java", text)

        assertForeground(text, "HACK", keywordColor)
        assertForeground(text, "[auth]", Config.tagColor("auth"))
        assertForeground(text, "(high)", Config.priorityColor("high")!!)
        assertForeground(text, "refresh token", descriptionColor)
    }

    fun testDoneKeywordIsHighlighted() {
        // The highlighter now matches the same set as the scanner (user keywords + DONE), so a
        // completed comment is colorized in the editor, not just listed in the tool window.
        val text = "class S {\n  // DONE wrapped up\n}"
        paint("S.java", text)
        assertForeground(text, "DONE", Config.keywordColor("DONE"))
    }

    fun testUpperCasePriorityGetsThePriorityColor() {
        // (HIGH) is lower-cased before lookup, so it colors the same as (high) rather than falling
        // back to no color.
        val text = "class S {\n  // HACK (HIGH) shout\n}"
        paint("S.java", text)
        assertForeground(text, "(HIGH)", Config.priorityColor("high")!!)
    }

    /* ============ Helpers ============ */

    private fun paint(fileName: String, text: String) {
        myFixture.configureByText(fileName, text)
        TodoHighlightPainter.refresh(myFixture.editor)
    }

    /** Foreground color the painter applied at [offset], or null if none. */
    private fun foregroundAt(offset: Int): Color? {
        return myFixture.editor.markupModel.allHighlighters
            .asSequence()
            .filter { it.startOffset <= offset && offset < it.endOffset }
            .mapNotNull { it.getTextAttributes(null)?.foregroundColor }
            .firstOrNull()
    }

    private fun assertForeground(text: String, substring: String, expected: Color) {
        val offset = text.indexOf(substring)
        assertTrue("'$substring' not found in fixture", offset >= 0)
        assertForegroundAt(offset, expected)
    }

    private fun assertForegroundAt(offset: Int, expected: Color) {
        assertEquals("Wrong foreground color at offset $offset", expected, foregroundAt(offset))
    }

    private fun assertNotForegroundAt(offset: Int, unexpected: Color) {
        assertFalse("Did not expect color $unexpected at offset $offset", unexpected == foregroundAt(offset))
    }

    /** Offset of the closing delimiter (its last occurrence in the fixture text). */
    private fun closingOffset(text: String, suffix: String): Int {
        val offset = text.lastIndexOf(suffix)
        assertTrue("Closing delimiter '$suffix' not found in fixture", offset >= 0)
        return offset
    }
}
