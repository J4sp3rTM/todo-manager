package io.github.j4sp3rtm.todomanager

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the scanner recognizes completed (DONE) comments and lifts the "done by … on …"
 * stamp out of the description so it can be shown — struck through — in the tool window.
 */
class TodoScannerDoneTest : BasePlatformTestCase() {

    fun testDoneCommentIsMarkedDoneWithSignature() {
        val item = scanSingle("S.java", "class S {\n  // DONE fix login (done by Jasper on 2026-06-29)\n}")

        assertEquals("DONE", item.keyword)
        assertTrue("Expected item to be marked done", item.done)
        assertEquals("Jasper", item.doneBy)
        assertEquals("2026-06-29", item.doneAt)
        // The stamp is stripped from the description so the badge isn't duplicated.
        assertEquals("fix login", item.description)
    }

    fun testOpenTodoIsNotDone() {
        val item = scanSingle("S.java", "class S {\n  // TODO fix login\n}")

        assertEquals("TODO", item.keyword)
        assertFalse(item.done)
        assertNull(item.doneBy)
    }

    fun testDoneInBlockCommentKeepsDelimiter() {
        val item = scanSingle("S.java", "class S {\n  /* DONE fix login (done by Jasper on 2026-06-29) */\n}")

        assertTrue(item.done)
        assertEquals("Jasper", item.doneBy)
        assertEquals("fix login", item.description)
    }

    private fun scanSingle(fileName: String, text: String): TodoItem {
        val psiFile = myFixture.configureByText(fileName, text)
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        val items = TodoScanner.scan(psiFile, document, psiFile.virtualFile)
        assertEquals("Expected exactly one scanned item", 1, items.size)
        return items.first()
    }
}
