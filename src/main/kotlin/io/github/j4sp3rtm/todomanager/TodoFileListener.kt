package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Listens for file changes and triggers re-scan of modified Java files.
 */
class TodoFileListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val changedFiles = events
            .filterIsInstance<VFileContentChangeEvent>()
            .mapNotNull { it.file }
            .filter { it.isValid && !it.isDirectory && !it.fileType.isBinary }

        if (changedFiles.isEmpty()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val service = TodoScannerService.getInstance(project)
            for (file in changedFiles) {
                service.refreshFile(file)
            }
        }
    }
}
