package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.Alarm

/**
 * Wires up editor TODO highlighting for a project: paints already-open editors, repaints on edits
 * (debounced) and whenever an editor opens. Highlighting itself is done by [TodoHighlightPainter],
 * which writes into the editor markup model so it works for every language regardless of whether
 * the IDE enables code-insight for the file.
 */
class TodoHighlightingActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val factory = EditorFactory.getInstance()
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)

        // Repaint when an editor opens.
        factory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project == project) onEdt(project) { TodoHighlightPainter.refresh(editor) }
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                TodoHighlightPainter.clear(event.editor)
            }
        }, project)

        // Repaint affected editors after edits, debounced so typing stays responsive.
        factory.eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val document = event.document
                val editors = factory.getEditors(document, project)
                if (editors.isEmpty()) return
                alarm.cancelAllRequests()
                alarm.addRequest({
                    if (!project.isDisposed) editors.forEach { TodoHighlightPainter.refresh(it) }
                }, REPAINT_DELAY_MS)
            }
        }, project)

        // Paint editors that are already open when the project starts.
        onEdt(project) {
            factory.allEditors
                .filter { it.project == project && FileDocumentManager.getInstance().getFile(it.document) != null }
                .forEach { TodoHighlightPainter.refresh(it) }
        }
    }

    private fun onEdt(project: Project, action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({ action() }) { project.isDisposed }
    }

    private companion object {
        const val REPAINT_DELAY_MS = 250
    }
}
