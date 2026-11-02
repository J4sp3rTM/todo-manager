package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Large-project behaviour of [TodoScannerService]. The scan runs on a pooled thread while the test
 * thread (the EDT) keeps taking write actions, which is what the IDE does when the user types or a
 * file changes; the longest wait for the write lock is what the user feels as a freeze.
 *
 * [testScanOfRealRepository] only runs when `-PbigRepo=<path>` points at a checked-out repository.
 */
class TodoScannerStressTest : BasePlatformTestCase() {

    private var extraRoot: VirtualFile? = null

    override fun tearDown() {
        try {
            extraRoot?.let { PsiTestUtil.removeContentEntry(module, it) }
        } finally {
            super.tearDown()
        }
    }

    fun testWritersAreNotStarvedDuringFullScan() {
        val body = (1..300).joinToString("\n") { "  int field$it = $it; // plain comment $it" }
        for (dir in 0 until 30) {
            for (file in 0 until 100) {
                myFixture.tempDirFixture.createFile(
                    "d$dir/sub/F$file.java",
                    "class F$file {\n  // TODO item $dir-$file\n$body\n}",
                )
            }
        }

        val service = TodoScannerService.getInstance(project)
        val stats = scanWhileWriting(service)

        println("STRESS synthetic: files=3000 scan=${stats.scanMs}ms maxWriteWait=${stats.maxWaitMs}ms writes=${stats.writes}")
        assertEquals(3000, service.items.size)
        assertTrue("a write action waited ${stats.maxWaitMs}ms for the scan", stats.maxWaitMs < MAX_WRITE_WAIT_MS)
    }

    fun testBurstOfScanRequestsRunsOneScan() {
        myFixture.addFileToProject("A.java", "class A {\n  // TODO one\n}")
        val service = TodoScannerService.getInstance(project)
        val notifications = AtomicInteger()
        service.addChangeListener { notifications.incrementAndGet() }

        repeat(50) { service.scheduleRefresh() }

        waitUntil { notifications.get() >= 1 }
        Thread.sleep(1_000)
        assertEquals(1, notifications.get())
    }

    fun testScanOfRealRepository() {
        val path = System.getProperty("todo.bigRepo") ?: return
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path))
        assertNotNull("not a directory: $path", root)
        extraRoot = root!!.also { PsiTestUtil.addContentRoot(module, it) }

        val service = TodoScannerService.getInstance(project)
        val stats = scanWhileWriting(service)

        println(
            "STRESS repo=$path scan=${stats.scanMs}ms items=${service.items.size} " +
                "maxWriteWait=${stats.maxWaitMs}ms writes=${stats.writes}"
        )
        assertTrue("a write action waited ${stats.maxWaitMs}ms for the scan", stats.maxWaitMs < MAX_WRITE_WAIT_MS)
    }

    private class Stats(val scanMs: Long, val maxWaitMs: Long, val writes: Int)

    /** Runs a full scan on a pooled thread and, meanwhile, measures how long write actions wait. */
    private fun scanWhileWriting(service: TodoScannerService): Stats {
        val started = System.nanoTime()
        val scan: Future<*> = ApplicationManager.getApplication().executeOnPooledThread { service.refresh() }
        var maxWaitNs = 0L
        var writes = 0
        while (!scan.isDone) {
            val t = System.nanoTime()
            runWriteAction { }
            maxWaitNs = maxOf(maxWaitNs, System.nanoTime() - t)
            writes++
            Thread.sleep(5)
        }
        scan.get(1, TimeUnit.MINUTES)
        val scanMs = (System.nanoTime() - started) / 1_000_000
        return Stats(scanMs, maxWaitNs / 1_000_000, writes)
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue("timed out", System.currentTimeMillis() < deadline)
            Thread.sleep(20)
        }
    }

    companion object {
        private const val MAX_WRITE_WAIT_MS = 500L
    }
}
