package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Whole-project scan behaviour of [TodoScannerService]: a file must be listed once even when several
 * content roots overlap (typical for Gradle/Maven modules whose source-set modules nest under the
 * project root module), instead of once per root that reaches it.
 */
class TodoScannerServiceTest : BasePlatformTestCase() {

    private var extraRoot: VirtualFile? = null

    override fun tearDown() {
        try {
            extraRoot?.let { PsiTestUtil.removeContentEntry(module, it) }
        } finally {
            super.tearDown()
        }
    }

    fun testFileUnderOverlappingContentRootsIsListedOnce() {
        myFixture.addFileToProject("sub/A.java", "class A {\n  // TODO one\n}")
        extraRoot = myFixture.findFileInTempDir("sub").also { PsiTestUtil.addContentRoot(module, it) }

        val service = TodoScannerService.getInstance(project)
        service.refresh()

        assertEquals(1, service.items.size)
    }

    fun testNestedFilesAreFoundAndJunkDirectoriesAreSkipped() {
        myFixture.addFileToProject("a/b/c/Deep.java", "class Deep {\n  // TODO deep\n}")
        myFixture.addFileToProject("Top.java", "class Top {\n  // TODO top\n}")
        myFixture.addFileToProject("node_modules/lib/Junk.java", "class Junk {\n  // TODO junk\n}")

        val service = TodoScannerService.getInstance(project)
        service.refresh()

        assertEquals(setOf("deep", "top"), service.items.map { it.description }.toSet())
    }

    fun testRepeatedRefreshesEndWithTheCompleteResult() {
        myFixture.addFileToProject("A.java", "class A {\n  // TODO one\n}")
        myFixture.addFileToProject("B.java", "class B {\n  // TODO two\n}")
        val service = TodoScannerService.getInstance(project)

        service.refresh()
        service.refresh()

        assertEquals(2, service.items.size)
    }
}
