package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Regression test: right after a file changes, its document is reloaded before the PSI is reparsed,
 * so PSI comment offsets can momentarily point past the (shorter) document. Scanning must skip those
 * matches rather than crash `Document.getLineNumber` with an IndexOutOfBoundsException.
 */
class TodoScannerOffsetGuardTest : BasePlatformTestCase() {

    fun testOffsetsPastDocumentEndAreSkippedNotCrash() {
        val text = "class S {\n  // TODO fix the thing\n}"
        val psiFile = myFixture.configureByText("S.java", text)

        // A document far shorter than the PSI — the TODO's offsets fall outside it.
        val shrunkDocument = EditorFactory.getInstance().createDocument("x")

        val items = TodoScanner.scan(psiFile, shrunkDocument, psiFile.virtualFile)

        // No crash, and the out-of-range match is dropped rather than reported at a bogus offset.
        assertTrue("Out-of-range matches should be skipped", items.isEmpty())
    }

    fun testInSyncDocumentStillScans() {
        val text = "class S {\n  // TODO fix the thing\n}"
        val psiFile = myFixture.configureByText("S.java", text)
        val document = myFixture.getDocument(psiFile)

        val items = TodoScanner.scan(psiFile, document, psiFile.virtualFile)

        assertEquals(1, items.size)
        assertEquals("TODO", items.first().keyword)
    }
}
