package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Renames the editor tab of the file currently opened via the tool window's single-click preview
 * (see [TodoPreviewTracker]) so it's obvious which tab is the reusable preview, e.g.
 * "Preview: Login.ts". Returns null for every other file, which keeps the platform's default title.
 */
class TodoPreviewTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (!Config.PREVIEW_ON_SINGLE_CLICK) return null
        if (TodoPreviewTracker.getInstance(project).file != file) return null
        // Editing promotes a preview tab to a permanent one — drop the prefix along with it.
        if (FileDocumentManager.getInstance().isFileModified(file)) return null
        return "Preview: ${file.presentableName}"
    }
}
