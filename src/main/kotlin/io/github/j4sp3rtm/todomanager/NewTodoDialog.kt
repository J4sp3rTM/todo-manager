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
    private val priorityCombo = JComboBox(arrayOf("") + Config.PRIORITIES)
    private val descriptionField = JTextField(30)
    private val generalCheckBox = JCheckBox("General TODO (not attached to code)").apply {
        toolTipText = "Store this TODO in the panel instead of inserting a comment at the caret"
    }

    val keyword: String get() = keywordCombo.selectedItem?.toString()?.uppercase() ?: "TODO"
    val tag: String get() = tagField.text?.trim() ?: ""
    val priority: String get() = priorityCombo.selectedItem?.toString() ?: ""
    val description: String get() = descriptionField.text?.trim() ?: ""

    /** When true, the TODO is stored as a general (code-free) item rather than inserted as a comment. */
    val isGeneral: Boolean get() = generalCheckBox.isSelected

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
            add(Box.createVerticalStrut(8))
            add(JPanel(java.awt.BorderLayout()).apply {
                alignmentX = JPanel.LEFT_ALIGNMENT
                add(generalCheckBox, java.awt.BorderLayout.WEST)
                maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
            })
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
