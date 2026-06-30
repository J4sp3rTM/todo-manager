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

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    /** Rescans the entire project for TODO items. Call from a background thread. */
    fun refresh() {
        codeItems = runReadAction {
            scanProject()
        }
        notifyListeners()
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

    private fun scanProject(): List<TodoItem> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val excludedNames = Config.EXCLUDED_DIR_NAMES.map { it.lowercase() }.toHashSet()
        val sourceNames = Config.SOURCE_DIR_NAMES.map { it.lowercase() }.toHashSet()

        val bases = mutableListOf<VirtualFile>()
        val allItems = mutableListOf<TodoItem>()
        var anySourceDirFound = false
        val roots = ProjectRootManager.getInstance(project).contentRoots
        for (root in roots) {
            val rootBases = if (Config.LIMIT_TO_SOURCE_DIRS) {
                // Limiting is strict: with no source dir under this root, scan nothing here (rather
                // than silently falling back to the whole root) so the empty result matches the
                // "add a source folder" banner the tool window shows.
                val sourceDirs = findSourceDirs(root, sourceNames, excludedNames, fileIndex)
                if (sourceDirs.isNotEmpty()) anySourceDirFound = true
                sourceDirs
            } else {
                listOf(root)
            }
            bases.addAll(rootBases)
            for (base in rootBases) {
                collectScannableFiles(base, excludedNames, fileIndex).forEach { vFile ->
                    allItems.addAll(scanFile(vFile))
                }
            }
        }
        scanBases = bases
        noSourceDirFound = Config.LIMIT_TO_SOURCE_DIRS && roots.isNotEmpty() && !anySourceDirFound
        return allItems
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
    ): List<VirtualFile> {
        val found = mutableListOf<VirtualFile>()
        fun search(dir: VirtualFile) {
            for (child in dir.children) {
                if (!child.isDirectory || isPrunedDir(child, excludedNames, fileIndex)) continue
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
    ): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        fun walk(file: VirtualFile) {
            if (file.isDirectory) {
                if (isPrunedDir(file, excludedNames, fileIndex)) return
                file.children.forEach { walk(it) }
            } else if (isScannable(file) && !isExcludedByIde(file, fileIndex)) {
                result.add(file)
            }
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
        fun getInstance(project: Project): TodoScannerService =
            project.getService(TodoScannerService::class.java)
    }
}
