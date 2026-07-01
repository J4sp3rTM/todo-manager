package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * First-run onboarding tour. Three short, visual steps: markers highlighted in code, the one-line
 * format, and where to manage everything. Each step is a painted illustration ([OnboardingVisuals])
 * plus a single caption — kept deliberately light so it reads in a few seconds.
 *
 * Shown once per installation by [OnboardingActivity]; re-openable via the
 * "TODO Manager: Getting Started" action.
 */
class OnboardingDialog(private val project: Project) : DialogWrapper(project) {

    /** One step of the tour: heading, caption, the illustration, and an optional footer row. */
    private class Step(
        val heading: String,
        val caption: String,
        val visual: JComponent,
        val footer: JComponent? = null,
    )

    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout)
    private val progressLabel = JLabel()

    /** Last step: whether to open the New TODO dialog on finish so the user creates one hands-on. */
    private val createFirstCheckBox = JCheckBox("Create my first TODO now", true)

    private val steps = listOf(
        Step(
            heading = "Your markers, highlighted",
            caption = "TODO, FIXME, HACK, NOTE and XXX comments stand out right in your code — in any language.",
            visual = EditorVisual(),
        ),
        Step(
            heading = "One simple format",
            caption = "Keyword first. Tag and priority are optional.",
            visual = SyntaxVisual(),
        ),
        Step(
            heading = "Manage them in one place",
            caption = "Open the TODO Manager at the bottom to browse, edit, and jump to any marker.",
            visual = ToolWindowVisual(),
            footer = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(8)
                add(createFirstCheckBox, BorderLayout.WEST)
            },
        ),
    )

    private var current = 0

    private lateinit var backAction: Action

    init {
        title = "TODO Manager — Getting Started"
        init()
        updateStep()
    }

    override fun createCenterPanel(): JComponent {
        steps.forEachIndexed { i, step -> cards.add(renderStep(step), i.toString()) }
        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(560, 400)
            border = JBUI.Borders.empty(8, 12)
            add(header(), BorderLayout.NORTH)
            add(cards, BorderLayout.CENTER)
        }
    }

    /* ============ Layout ============ */

    private fun header(): JComponent {
        val icon = JLabel(IconLoader.getIcon("/icons/todo.svg", javaClass))
        val title = JLabel("TODO Highlighter & Comment Manager").apply {
            font = font.deriveFont(Font.BOLD, font.size + 2f)
        }
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyBottom(12)
            add(icon)
            add(Box.createHorizontalStrut(8))
            add(title)
            add(Box.createHorizontalGlue())
            add(progressLabel)
        }
    }

    /** Lays a [Step] out as heading (top), illustration (centre), caption + optional footer (bottom). */
    private fun renderStep(step: Step): JComponent {
        val south = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyTop(10)
            add(JLabel("<html><body style='width:520px'>${step.caption}</body></html>").apply {
                foreground = JBColor.foreground()
                alignmentX = Component.LEFT_ALIGNMENT
            })
            step.footer?.let {
                it.alignmentX = Component.LEFT_ALIGNMENT
                add(it)
            }
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel(step.heading).apply {
                font = font.deriveFont(Font.BOLD, font.size + 3f)
                border = JBUI.Borders.emptyBottom(8)
            }, BorderLayout.NORTH)
            add(step.visual, BorderLayout.CENTER)
            add(south, BorderLayout.SOUTH)
        }
    }

    /* ============ Navigation ============ */

    override fun createActions(): Array<Action> {
        backAction = object : AbstractAction("Back") {
            override fun actionPerformed(e: ActionEvent) {
                if (current > 0) { current--; updateStep() }
            }
        }
        // The OK action doubles as "Next" until the last step, where it becomes "Finish".
        return arrayOf(backAction, okAction)
    }

    override fun createLeftSideActions(): Array<Action> = arrayOf(
        object : AbstractAction("Skip") {
            override fun actionPerformed(e: ActionEvent) = doCancelAction()
        }
    )

    override fun doOKAction() {
        if (current < steps.lastIndex) {
            current++
            updateStep()
            return
        }
        val createFirst = createFirstCheckBox.isSelected
        super.doOKAction()
        finishTour(createFirst)
    }

    private fun updateStep() {
        cardLayout.show(cards, current.toString())
        progressLabel.text = steps.indices.joinToString("  ") { if (it == current) "●" else "○" }
        progressLabel.foreground = JBColor.GRAY
        backAction.isEnabled = current > 0
        setOKButtonText(if (current == steps.lastIndex) "Finish" else "Next")
    }

    /* ============ Completion ============ */

    private fun finishTour(createFirst: Boolean) {
        ToolWindowManager.getInstance(project).getToolWindow("TODO Manager")?.activate(null, true)
        if (createFirst) invokeLater { openNewTodoDialog() }
    }

    /** Mirrors the tool window's "+ New TODO" flow so the tour ends with a real, hands-on creation. */
    private fun openNewTodoDialog() {
        val dialog = NewTodoDialog(project)
        if (!dialog.showAndGet()) return

        val keyword = dialog.keyword
        val tag = dialog.tag.ifBlank { null }
        val priority = dialog.priority.ifBlank { null }
        val description = dialog.description
        val scanner = TodoScannerService.getInstance(project)

        if (dialog.isGeneral) {
            scanner.addGeneralTodo(keyword, tag, priority, description)
            return
        }
        if (FileEditorManager.getInstance(project).selectedTextEditor == null) {
            Messages.showInfoMessage(
                project,
                "Open a file and place the caret where the comment should go, " +
                    "or check \"General TODO\" to store it without code.",
                "No Active Editor"
            )
            return
        }
        TodoEditor.insertNew(project, keyword, tag, priority, description)
        ApplicationManager.getApplication().executeOnPooledThread { scanner.refresh() }
    }
}
