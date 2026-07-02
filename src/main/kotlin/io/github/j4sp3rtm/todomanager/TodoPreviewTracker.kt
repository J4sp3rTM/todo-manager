package io.github.j4sp3rtm.todomanager

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tracks which file is currently open as the tool window's single-click "preview" tab.
 *
 * Lives as a project service (rather than panel state) so [TodoPreviewTabTitleProvider] can read
 * it when the platform asks for tab titles. Changing the tracked file re-computes the tab title of
 * both the old and new file, so the "Preview:" prefix appears and disappears as the preview moves.
 */
@Service(Service.Level.PROJECT)
class TodoPreviewTracker(private val project: Project) {

    /** The file opened by single-click preview, or null when nothing is being previewed. */
    var file: VirtualFile? = null
        private set

    fun setPreview(newFile: VirtualFile?) {
        val old = file
        if (old == newFile) return
        file = newFile
        // Recompute tab titles when a tab whose preview state changed is on screen. There is no
        // stable per-file API for this (FileEditorManagerImpl.updateFilePresentation is internal),
        // so nudge all tabs via the UI-settings-changed event — the standard public trigger.
        val manager = FileEditorManager.getInstance(project)
        val affectsOpenTab = (old != null && manager.isFileOpen(old)) ||
            (newFile != null && manager.isFileOpen(newFile))
        if (affectsOpenTab) UISettings.getInstance().fireUISettingsChanged()
    }

    companion object {
        fun getInstance(project: Project): TodoPreviewTracker =
            project.getService(TodoPreviewTracker::class.java)
    }
}
