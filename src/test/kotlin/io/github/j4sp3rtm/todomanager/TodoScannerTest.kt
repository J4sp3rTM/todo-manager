package io.github.j4sp3rtm.todomanager

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end scanning behaviour: that [TodoScanner] turns comments into [TodoItem]s with the right
 * keyword/tag/priority/description, normalizes the priority to lower case (so coloring and grouping
 * are consistent no matter how the comment was cased), and only picks up configured keywords.
 *
 * Complements [TodoScannerDoneTest] (which focuses on DONE handling).
 */
class TodoScannerTest : BasePlatformTestCase() {

    fun testStructuredCommentIsFullyParsed() {
        val item = scanSingle("S.java", "class S {\n  // TODO [auth] (high) fix login\n}")
        assertEquals("TODO", item.keyword)
        assertEquals("auth", item.tag)
        assertEquals("high", item.priority)
        assertEquals("fix login", item.description)
    }

    fun testPriorityIsNormalizedToLowerCase() {
        // A comment written as (HIGH) must resolve to the same "high" the color/group logic expects.
        val item = scanSingle("S.java", "class S {\n  // TODO (HIGH) shout\n}")
        assertEquals("high", item.priority)
    }

    fun testKeywordIsCanonicalizedToUpperCase() {
        val item = scanSingle("S.java", "class S {\n  // fixme lower cased\n}")
        assertEquals("FIXME", item.keyword)
        // The source casing is preserved separately so edits don't rewrite it.
        assertEquals("fixme", item.matchedKeyword)
    }

    fun testUnknownPriorityStaysInDescription() {
        val item = scanSingle("S.java", "class S {\n  // TODO (urgent) later\n}")
        assertNull(item.priority)
        assertEquals("(urgent) later", item.description)
    }

    fun testMultipleTodosInOneBlockCommentAreSeparateItems() {
        val items = scanAll("S.java", "class S {\n  /*\n   TODO first\n   FIXME second\n   */\n}")
        assertEquals(2, items.size)
        assertEquals(setOf("TODO", "FIXME"), items.map { it.keyword }.toSet())
    }

    fun testCommentWithoutAKeywordProducesNoItems() {
        assertTrue(scanAll("S.java", "class S {\n  // nothing worth tracking here\n}").isEmpty())
    }

    /* ============ Helpers ============ */

    private fun scanAll(fileName: String, text: String): List<TodoItem> {
        val psiFile = myFixture.configureByText(fileName, text)
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        return TodoScanner.scan(psiFile, document, psiFile.virtualFile)
    }

    private fun scanSingle(fileName: String, text: String): TodoItem {
        val items = scanAll(fileName, text)
        assertEquals("Expected exactly one scanned item", 1, items.size)
        return items.first()
    }
}
