package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.*

/**
 * Dialog for creating a new TODO comment.
 */
class NewTodoDialog(project: Project) : DialogWrapper(project) {

    private val keywordCombo = JComboBox(Config.KEYWORDS.toTypedArray()).apply {
        isEditable = true
        selectedItem = "TODO"
    }
    private val tagField = JTextField(15)
    private val priorityCombo = JComboBox(arrayOf("", "critical", "high", "medium", "low"))
    private val descriptionField = JTextField(30)

    val keyword: String get() = keywordCombo.selectedItem?.toString()?.uppercase() ?: "TODO"
    val tag: String get() = tagField.text?.trim() ?: ""
    val priority: String get() = priorityCombo.selectedItem?.toString() ?: ""
    val description: String get() = descriptionField.text?.trim() ?: ""

    init {
        title = "New TODO"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

            add(labeledRow("Keyword:", keywordCombo))
            add(Box.createVerticalStrut(6))
            add(labeledRow("Tag (optional):", tagField))
            add(Box.createVerticalStrut(6))
            add(labeledRow("Priority (optional):", priorityCombo))
            add(Box.createVerticalStrut(6))
            add(labeledRow("Description:", descriptionField))
        }
        return panel
    }

    private fun labeledRow(label: String, component: JComponent): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JLabel(label))
            add(Box.createHorizontalStrut(8))
            add(component)
            add(Box.createHorizontalGlue())
            maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    override fun doOKAction() {
        if (description.isBlank()) {
            JOptionPane.showMessageDialog(contentPanel, "Description cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        super.doOKAction()
    }
}
