package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Keeps the scanner in sync with the virtual file system: re-scans changed/created/moved/renamed
 * files, drops deleted ones, and falls back to a full rescan for directory-level changes (VFS fires
 * only one event for a whole subtree, so the individual files inside aren't enumerated here).
 */
class TodoFileListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val filesToRescan = mutableListOf<VirtualFile>()
        val pathsToRemove = mutableListOf<String>()
        var needsFullRefresh = false

        for (event in events) {
            when (event) {
                is VFileContentChangeEvent -> event.file?.let { filesToRescan.add(it) }
                is VFileCreateEvent -> {
                    val file = event.file
                    if (file == null || file.isDirectory) needsFullRefresh = true else filesToRescan.add(file)
                }
                is VFileMoveEvent ->
                    if (event.file.isDirectory) needsFullRefresh = true else filesToRescan.add(event.file)
                is VFilePropertyChangeEvent ->
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        if (event.file.isDirectory) needsFullRefresh = true else filesToRescan.add(event.file)
                    }
                is VFileCopyEvent -> {
                    val created = event.findCreatedFile()
                    if (created == null || created.isDirectory) needsFullRefresh = true else filesToRescan.add(created)
                }
                // Captured by path because the VirtualFile is already invalid once the delete is applied.
                is VFileDeleteEvent -> pathsToRemove.add(event.path)
            }
        }

        if (!needsFullRefresh && filesToRescan.isEmpty() && pathsToRemove.isEmpty()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = TodoScannerService.getInstance(project)
            if (needsFullRefresh) {
                service.refresh()
                return@executeOnPooledThread
            }
            pathsToRemove.forEach { service.removeFilesUnder(it) }
            filesToRescan
                .filter { it.isValid && !it.isDirectory && !it.fileType.isBinary }
                .forEach { service.refreshFile(it) }
        }
    }
}
