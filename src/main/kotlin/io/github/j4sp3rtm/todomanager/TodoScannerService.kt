package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Project-level service that scans for TODO items across source files and caches results.
 *
 * The scan is scoped (see *Settings > Tools > TODO Manager > Scanning Scope*):
 *  - folders the IDE marks excluded and files belonging to libraries are skipped,
 *  - common junk directories (node_modules, build, …) are pruned,
 *  - scanning is restricted to detected source directories (src, app, …) when present.
 */
@Service(Service.Level.PROJECT)
class TodoScannerService(private val project: Project) {

    /** TODO comments found by the last scan. The general (code-free) todos are merged in by [items]. */
    @Volatile
    private var codeItems: List<TodoItem> = emptyList()

    /** All items shown in the tool window: scanned code comments plus general (code-free) todos. */
    val items: List<TodoItem> get() = codeItems + generalItems()

    /** Base directories covered by the last full scan, used to scope incremental refreshes. */
    @Volatile
    private var scanBases: List<VirtualFile> = emptyList()

    /**
     * True when source-dir limiting is on but no source directory was found, so the scan silently
     * fell back to whole content roots. The tool window surfaces this so the user can either add a
     * source folder or turn the limit off. Null/false when limiting is off or a source dir was found.
     */
    @Volatile
    var noSourceDirFound: Boolean = false
        private set

    /** Listeners notified when items change. */
    private val listeners = mutableListOf<() -> Unit>()

    private val generalStore get() = GeneralTodoStore.getInstance(project)

    /** Bumped by every [refresh]; a running full scan that sees a newer value was superseded and stops. */
    private val scanGeneration = AtomicInteger()

    private var pendingRefresh: ScheduledFuture<*>? = null

    /** Thrown inside a full scan that was superseded by a newer one, or whose project was closed. */
    private class ScanAborted : RuntimeException(null, null, false, false)

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    /**
     * Rescans the entire project for TODO items. Call from a background thread.
     *
     * Nothing here holds the read lock for long: the file tree is walked one directory per read
     * action and each file is parsed in its own read action, so a pending write action (the EDT
     * waiting on its write-intent lock) only ever waits for a single directory or file. A scan is
     * abandoned as soon as a newer [refresh] starts or the project closes.
     */
    fun refresh() {
        val generation = scanGeneration.incrementAndGet()
        val items = try {
            val files = collectProjectFiles(generation)
            val found = ArrayList<TodoItem>()
            for (file in files) {
                found += readStep(generation) { scanFile(file) }
            }
            checkScanActive(generation)
            found
        } catch (aborted: ScanAborted) {
            return
        }
        codeItems = items
        notifyListeners()
    }

    /**
     * Requests a full [refresh] shortly from now, merging bursts of requests into one. Directory-level
     * file events (checkout, build output, generators) arrive in bursts; scanning once after the
     * burst is enough.
     */
    fun scheduleRefresh() {
        synchronized(this) {
            pendingRefresh?.cancel(false)
            pendingRefresh = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                { if (!project.isDisposed) refresh() },
                REFRESH_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun checkScanActive(generation: Int) {
        if (project.isDisposed || generation != scanGeneration.get()) throw ScanAborted()
    }

    /** Runs [action] in its own short read action, unless the scan was abandoned in the meantime. */
    private fun <T> readStep(generation: Int, action: () -> T): T {
        checkScanActive(generation)
        return runReadAction(action)
    }

    /** Rescans a single file and merges results, respecting the current scan scope. */
    fun refreshFile(virtualFile: VirtualFile) {
        // null = the file's PSI hasn't caught up with its document yet (offsets would be wrong);
        // leave the cached items untouched and re-scan once everything is committed.
        val fileItems: List<TodoItem>? = runReadAction {
            when {
                !isInScope(virtualFile) -> emptyList()
                !isPsiInSyncWithDocument(virtualFile) -> null
                else -> scanFile(virtualFile)
            }
        }
        if (fileItems == null) {
            ApplicationManager.getApplication().invokeLater({
                PsiDocumentManager.getInstance(project).performWhenAllCommitted {
                    ApplicationManager.getApplication().executeOnPooledThread { refreshFile(virtualFile) }
                }
            }) { project.isDisposed }
            return
        }
        val otherItems = codeItems.filter { it.file != virtualFile }
        codeItems = otherItems + fileItems
        notifyListeners()
    }

    /**
     * Drops all cached items whose file lives at or under [path]. Used when a file or directory is
     * deleted: the [VirtualFile] is already invalid by then, so we match on the captured path. A
     * directory delete fires a single event, so removing the whole subtree here covers its children.
     */
    fun removeFilesUnder(path: String) {
        val prefix = "$path/"
        val remaining = codeItems.filter {
            val p = it.file?.path ?: return@filter true
            p != path && !p.startsWith(prefix)
        }
        if (remaining.size != codeItems.size) {
            codeItems = remaining
            notifyListeners()
        }
    }

    /**
     * True if [virtualFile]'s PSI and document agree in length. Right after a file changes, the
     * document is reloaded before the PSI is reparsed, so PSI offsets can momentarily point past the
     * document end — scanning then would crash [com.intellij.openapi.editor.Document.getLineNumber].
     */
    private fun isPsiInSyncWithDocument(virtualFile: VirtualFile): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return true
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return true
        return document.textLength == psiFile.textLength
    }

    /* ============ General (code-free) todos ============ */

    fun addGeneralTodo(keyword: String, tag: String?, priority: String?, description: String) {
        generalStore.add(keyword, tag, priority, description)
        notifyListeners()
    }

    fun updateGeneralTodo(id: String, mutate: (GeneralTodoStore.Entry) -> Unit) {
        generalStore.update(id, mutate)
        notifyListeners()
    }

    fun removeGeneralTodo(id: String) {
        generalStore.remove(id)
        notifyListeners()
    }

    /** Converts the stored general todos into [TodoItem]s for display. */
    private fun generalItems(): List<TodoItem> = generalStore.entries.map { e ->
        TodoItem(
            keyword = e.keyword,
            tag = e.tag,
            priority = e.priority,
            description = e.description,
            file = null,
            line = 0,
            textRange = null,
            matchRange = null,
            originalText = e.description,
            isBlockComment = false,
            source = TodoSource.GENERAL,
            generalId = e.id,
            done = e.done,
            doneBy = e.doneBy,
            doneAt = e.doneAt,
        )
    }

    /** Lists the files the full scan covers, one short read action per directory; also records the scan scope. */
    private fun collectProjectFiles(generation: Int): List<VirtualFile> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val excludedNames = Config.EXCLUDED_DIR_NAMES.map { it.lowercase() }.toHashSet()
        val sourceNames = Config.SOURCE_DIR_NAMES.map { it.lowercase() }.toHashSet()

        val bases = mutableListOf<VirtualFile>()
        val files = mutableListOf<VirtualFile>()
        // Content roots can overlap (e.g. Gradle/Maven source-set modules nested under the project
        // root module), so a file may be reachable from several bases; scan each file only once.
        val scannedFiles = HashSet<VirtualFile>()
        var anySourceDirFound = false
        val roots = readStep(generation) { ProjectRootManager.getInstance(project).contentRoots }
        for (root in roots) {
            val rootBases = if (Config.LIMIT_TO_SOURCE_DIRS) {
                // Limiting is strict: with no source dir under this root, scan nothing here (rather
                // than silently falling back to the whole root) so the empty result matches the
                // "add a source folder" banner the tool window shows.
                val sourceDirs = findSourceDirs(root, sourceNames, excludedNames, fileIndex, generation)
                if (sourceDirs.isNotEmpty()) anySourceDirFound = true
                sourceDirs
            } else {
                listOf(root)
            }
            bases.addAll(rootBases)
            for (base in rootBases) {
                collectScannableFiles(base, excludedNames, fileIndex, generation).filterTo(files) { scannedFiles.add(it) }
            }
        }
        scanBases = bases
        noSourceDirFound = Config.LIMIT_TO_SOURCE_DIRS && roots.isNotEmpty() && !anySourceDirFound
        return files
    }

    /** True if [file] would be included by the last full scan's scope. */
    private fun isInScope(file: VirtualFile): Boolean {
        if (!isScannable(file)) return false
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (isExcludedByIde(file, fileIndex)) return false
        if (scanBases.none { VfsUtilCore.isAncestor(it, file, false) }) return false
        val excludedNames = Config.EXCLUDED_DIR_NAMES.map { it.lowercase() }.toHashSet()
        // Reject if any ancestor folder is an excluded/junk directory.
        var parent = file.parent
        while (parent != null && scanBases.none { it == parent }) {
            if (parent.name.lowercase() in excludedNames) return false
            parent = parent.parent
        }
        return true
    }

    private fun scanFile(virtualFile: VirtualFile): List<TodoItem> {
        if (!isScannable(virtualFile)) return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return emptyList()
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        return TodoScanner.scan(psiFile, document, virtualFile)
    }

    /**
     * Auto-detects source directories (by name) under [root], pruning excluded/junk folders.
     * Once a directory matches, its whole subtree is included, so we stop descending into it.
     */
    private fun findSourceDirs(
        root: VirtualFile,
        sourceNames: Set<String>,
        excludedNames: Set<String>,
        fileIndex: ProjectFileIndex,
        generation: Int,
    ): List<VirtualFile> {
        val found = mutableListOf<VirtualFile>()
        fun search(dir: VirtualFile) {
            val subDirs = readStep(generation) {
                dir.children.filter { it.isDirectory && !isPrunedDir(it, excludedNames, fileIndex) }
            }
            for (child in subDirs) {
                if (child.name.lowercase() in sourceNames) {
                    found.add(child)
                } else {
                    search(child)
                }
            }
        }
        search(root)
        return found
    }

    private fun collectScannableFiles(
        base: VirtualFile,
        excludedNames: Set<String>,
        fileIndex: ProjectFileIndex,
        generation: Int,
    ): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        fun walk(file: VirtualFile) {
            val subDirs = readStep(generation) {
                val dirs = mutableListOf<VirtualFile>()
                if (!file.isDirectory) {
                    if (isScannable(file) && !isExcludedByIde(file, fileIndex)) result.add(file)
                } else if (!isPrunedDir(file, excludedNames, fileIndex)) {
                    for (child in file.children) {
                        if (child.isDirectory) dirs.add(child)
                        else if (isScannable(child) && !isExcludedByIde(child, fileIndex)) result.add(child)
                    }
                }
                dirs
            }
            subDirs.forEach { walk(it) }
        }
        walk(base)
        return result
    }

    /** A directory is pruned if its name is excluded or (optionally) the IDE excludes it. */
    private fun isPrunedDir(dir: VirtualFile, excludedNames: Set<String>, fileIndex: ProjectFileIndex): Boolean {
        if (dir.name.lowercase() in excludedNames) return true
        return isExcludedByIde(dir, fileIndex)
    }

    private fun isExcludedByIde(file: VirtualFile, fileIndex: ProjectFileIndex): Boolean {
        if (!Config.RESPECT_IDE_EXCLUDES) return false
        return fileIndex.isExcluded(file) || fileIndex.isInLibrary(file)
    }

    /** A file is scannable if it is a regular, valid, non-binary text file. */
    private fun isScannable(file: VirtualFile): Boolean =
        file.isValid && !file.isDirectory && !file.fileType.isBinary

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 300L

        fun getInstance(project: Project): TodoScannerService =
            project.getService(TodoScannerService::class.java)
    }
}
