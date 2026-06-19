package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * The main panel displayed in the TODO Manager tool window.
 * Contains a toolbar and a tree view of all TODO items.
 */
class TodoToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode("TODOs")
    private val scannerService = TodoScannerService.getInstance(project)

    init {
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = TodoCellRenderer()
        }

        // Double-click → navigate to source
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelected()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = tree.getClosestPathForLocation(e.x, e.y)
                    if (path != null) {
                        tree.selectionPath = path
                        showContextMenu(e)
                    }
                }
            }
        })

        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        // Listen for scan results
        scannerService.addChangeListener {
            invokeLater { rebuildTree() }
        }

        // Initial scan
        ApplicationManager.getApplication().executeOnPooledThread {
            scannerService.refresh()
        }
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
        }

        val refreshButton = JButton("Refresh").apply {
            toolTipText = "Rescan project for TODOs"
            addActionListener {
                ApplicationManager.getApplication().executeOnPooledThread {
                    scannerService.refresh()
                }
            }
        }

        val groupByCombo = ComboBoxWithWidePopup(arrayOf("FILE", "TAG", "PRIORITY")).apply {
            selectedItem = Config.GROUP_BY
            toolTipText = "Group items by"
            addActionListener {
                TodoManagerSettings.getInstance().state.groupBy = selectedItem as String
                rebuildTree()
            }
        }

        val addButton = JButton("+ New TODO").apply {
            toolTipText = "Insert a new TODO comment at cursor"
            addActionListener { showNewTodoDialog() }
        }

        toolbar.add(refreshButton)
        toolbar.add(JLabel("Group by:"))
        toolbar.add(groupByCombo)
        toolbar.add(addButton)

        return toolbar
    }

    /**
     * Rebuilds the tree from the current scan results, grouped by the selected mode.
     */
    private fun rebuildTree() {
        rootNode.removeAllChildren()
        val items = scannerService.items.sortedWith(compareBy({ it.file.path }, { it.line }))
        val groupBy = Config.GROUP_BY

        val grouped: Map<String, List<TodoItem>> = when (groupBy) {
            "TAG" -> items.groupBy { it.tag ?: "(no tag)" }
            "PRIORITY" -> items.groupBy { it.priority?.replaceFirstChar { c -> c.uppercase() } ?: "(no priority)" }
            else -> items.groupBy { it.file.name }
        }

        val sortedKeys = if (groupBy == "PRIORITY") {
            val order = listOf("Critical", "High", "Medium", "Low", "(no priority)")
            grouped.keys.sortedBy { key -> order.indexOf(key).let { if (it == -1) order.size else it } }
        } else {
            grouped.keys.sorted()
        }

        for (groupName in sortedKeys) {
            val groupItems = grouped[groupName] ?: continue
            val groupNode = DefaultMutableTreeNode(GroupLabel(groupName, groupItems.size))
            for (item in groupItems) {
                groupNode.add(DefaultMutableTreeNode(item))
            }
            rootNode.add(groupNode)
        }

        treeModel.reload()
        expandAll()
    }

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun navigateToSelected() {
        val item = getSelectedItem() ?: return
        val descriptor = OpenFileDescriptor(project, item.file, item.textRange.startOffset)
        descriptor.navigate(true)
    }

    private fun getSelectedItem(): TodoItem? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? TodoItem
    }

    /* ============ Context Menu ============ */

    private fun showContextMenu(e: MouseEvent) {
        val item = getSelectedItem() ?: return
        val menu = JPopupMenu()

        menu.add(JMenuItem("Go to Source").apply {
            addActionListener { navigateToSelected() }
        })
        menu.addSeparator()
        menu.add(JMenuItem("Edit Description...").apply {
            addActionListener { editDescription(item) }
        })
        menu.add(JMenuItem("Change Tag...").apply {
            addActionListener { editTag(item) }
        })
        menu.add(createPrioritySubMenu(item))
        menu.addSeparator()
        menu.add(JMenuItem("Mark as Done").apply {
            addActionListener { markDone(item) }
        })
        menu.add(JMenuItem("Delete TODO").apply {
            addActionListener { deleteTodo(item) }
        })

        menu.show(tree, e.x, e.y)
    }

    private fun createPrioritySubMenu(item: TodoItem): JMenu {
        return JMenu("Set Priority").apply {
            for (p in listOf("critical", "high", "medium", "low", null)) {
                val label = p?.replaceFirstChar { it.uppercase() } ?: "None"
                add(JMenuItem(label).apply {
                    addActionListener {
                        TodoEditor.setPriority(project, item, p)
                        refreshAfterEdit(item)
                    }
                })
            }
        }
    }

    /* ============ Editing Actions ============ */

    private fun editDescription(item: TodoItem) {
        val newDesc = Messages.showInputDialog(
            project,
            "New description:",
            "Edit TODO Description",
            null,
            item.description,
            null
        ) ?: return
        TodoEditor.setDescription(project, item, newDesc)
        refreshAfterEdit(item)
    }

    private fun editTag(item: TodoItem) {
        val newTag = Messages.showInputDialog(
            project,
            "Tag (leave empty to remove):",
            "Edit TODO Tag",
            null,
            item.tag ?: "",
            null
        ) ?: return
        TodoEditor.setTag(project, item, newTag.ifBlank { null })
        refreshAfterEdit(item)
    }

    private fun markDone(item: TodoItem) {
        TodoEditor.markDone(project, item)
        refreshAfterEdit(item)
    }

    private fun deleteTodo(item: TodoItem) {
        val ok = Messages.showYesNoDialog(
            project,
            "Delete this TODO comment from the source file?",
            "Delete TODO",
            Messages.getQuestionIcon()
        )
        if (ok == Messages.YES) {
            TodoEditor.delete(project, item)
            refreshAfterEdit(item)
        }
    }

    private fun showNewTodoDialog() {
        val dialog = NewTodoDialog(project)
        if (dialog.showAndGet()) {
            val keyword = dialog.keyword
            val tag = dialog.tag.ifBlank { null }
            val priority = dialog.priority.ifBlank { null }
            val description = dialog.description
            TodoEditor.insertNew(project, keyword, tag, priority, description)
            ApplicationManager.getApplication().executeOnPooledThread {
                scannerService.refresh()
            }
        }
    }

    private fun refreshAfterEdit(item: TodoItem) {
        ApplicationManager.getApplication().executeOnPooledThread {
            scannerService.refreshFile(item.file)
        }
    }
}

/** Label for group header nodes in the tree. */
data class GroupLabel(val name: String, val count: Int)

/**
 * Custom tree cell renderer — shows colored badges for keywords and priorities.
 */
class TodoCellRenderer : DefaultTreeCellRenderer() {

    init {
        // Remove the default opaque backgrounds that cause the light gray look
        isOpaque = false
        backgroundNonSelectionColor = null
        backgroundSelectionColor = null
    }

    override fun getTreeCellRendererComponent(
        tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val comp = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        // Ensure we always match the tree's background
        if (!selected) {
            background = tree.background
            (comp as? JComponent)?.isOpaque = false
        }
        val node = value as? DefaultMutableTreeNode ?: return comp
        val obj = node.userObject

        when (obj) {
            is GroupLabel -> {
                text = "${obj.name} (${obj.count})"
                icon = null
            }
            is TodoItem -> {
                val parts = mutableListOf<String>()
                // Keyword colored by type
                val kwColor = colorToHex(Config.keywordColor(obj.keyword))
                parts.add("<font color='$kwColor'><b>${obj.keyword}</b></font>")
                // Tag colored with deterministic palette
                if (obj.tag != null) {
                    val tColor = colorToHex(Config.tagColor(obj.tag))
                    parts.add("<font color='$tColor'><b>[${obj.tag}]</b></font>")
                }
                // Priority colored by severity
                if (obj.priority != null) {
                    val pColor = colorToHex(Config.priorityColor(obj.priority) ?: java.awt.Color.GRAY)
                    parts.add("<font color='$pColor'><b>(${obj.priority})</b></font>")
                }
                if (obj.description.isNotEmpty()) {
                    val dColor = colorToHex(Config.descriptionColor())
                    parts.add("<font color='$dColor'>${obj.description}</font>")
                }
                val locationInfo = "${obj.file.name}:${obj.line + 1}"
                text = "<html>${parts.joinToString(" ")} <font color='#888888'>— $locationInfo</font></html>"
                icon = null
            }
        }

        return comp
    }

    private fun colorToHex(c: java.awt.Color): String =
        String.format("#%02X%02X%02X", c.red, c.green, c.blue)
}
