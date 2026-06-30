package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * The main panel displayed in the TODO Manager tool window.
 * Contains a toolbar and a tree view of all TODO items.
 */
class TodoToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private companion object {
        /** Group-header label for general (code-free) todos when grouping by file. */
        const val GENERAL_GROUP = "General"
        /** CardLayout keys for the center area. */
        const val CARD_TREE = "tree"
        const val CARD_EMPTY = "empty"
    }

    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode("TODOs")
    private val scannerService = TodoScannerService.getInstance(project)

    /** Center swaps between the populated tree and a centered empty-state message via [centerLayout]. */
    private val centerLayout = CardLayout()
    private val centerPanel = JPanel(centerLayout)
    /**
     * Empty-state message shown in place of the tree. An HTML editor pane (not a label/link) so the
     * text wraps onto multiple lines as the tool window narrows, while keeping the settings link clickable.
     */
    private val emptyStatePane = JEditorPane()

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
        centerPanel.add(JBScrollPane(tree), CARD_TREE)
        centerPanel.add(createEmptyStateCard(), CARD_EMPTY)
        add(centerPanel, BorderLayout.CENTER)

        // Listen for scan results
        scannerService.addChangeListener {
            invokeLater { rebuildTree() }
        }

        // Initial scan, and warm the git-user cache so the first "Mark as Done" doesn't block the EDT.
        ApplicationManager.getApplication().executeOnPooledThread {
            GitUser.userName(project)
            scannerService.refresh()
        }
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(WrapLayout(FlowLayout.LEFT, 4, 2)).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
        }

        val groupByCombo = ComboBoxWithWidePopup(arrayOf("FILE", "TAG", "PRIORITY", "KEYWORD")).apply {
            selectedItem = Config.GROUP_BY
            toolTipText = "Group items by"
            addActionListener {
                TodoManagerSettings.getInstance().state.groupBy = selectedItem as String
                rebuildTree()
            }
        }

        // Keyword filter: "All", or a single keyword shown exclusively. Built from the configured
        // keywords (upper-cased to match the canonical form stored on each item).
        val keywordOptions = (listOf(Config.ALL_KEYWORDS) + Config.KEYWORDS.map { it.uppercase() }.distinct())
        if (Config.KEYWORD_FILTER !in keywordOptions) Config.KEYWORD_FILTER = Config.ALL_KEYWORDS
        val keywordFilterCombo = ComboBoxWithWidePopup(keywordOptions.toTypedArray()).apply {
            selectedItem = Config.KEYWORD_FILTER
            toolTipText = "Show only items with this keyword"
            addActionListener {
                Config.KEYWORD_FILTER = selectedItem as String
                rebuildTree()
            }
        }

        val addButton = JButton("+ New TODO").apply {
            toolTipText = "Insert a new TODO comment at cursor"
            addActionListener { showNewTodoDialog() }
        }

        val showDoneCheckBox = JCheckBox("Show done", Config.SHOW_DONE).apply {
            toolTipText = "Include completed (DONE) items in the list"
            addActionListener {
                Config.SHOW_DONE = isSelected
                rebuildTree()
            }
        }

        val collapseCheckBox = JCheckBox("Collapse", Config.COLLAPSE_BY_DEFAULT).apply {
            toolTipText = "Start groups collapsed (new groups on refresh also start collapsed)"
            addActionListener {
                Config.COLLAPSE_BY_DEFAULT = isSelected
                // Apply immediately to what's on screen; the setting then governs new groups on refresh.
                if (isSelected) collapseAllGroups() else expandAllGroups()
            }
        }

        val reverseCheckBox = JCheckBox("Reverse", Config.REVERSE_SORT).apply {
            toolTipText = "Reverse the group order and the items within each group"
            addActionListener {
                Config.REVERSE_SORT = isSelected
                rebuildTree()
            }
        }

        toolbar.add(JLabel("Group by:"))
        toolbar.add(groupByCombo)
        toolbar.add(JLabel("Keyword:"))
        toolbar.add(keywordFilterCombo)
        toolbar.add(addButton)
        toolbar.add(showDoneCheckBox)
        toolbar.add(collapseCheckBox)
        toolbar.add(reverseCheckBox)

        return toolbar
    }

    /**
     * The "nothing to show" card shown in place of the tree when it is empty. The HTML pane fills the
     * available width (so its text wraps when the tool window narrows) and is centered vertically; the
     * "Open settings" link, when present, navigates into the plugin's settings.
     */
    private fun createEmptyStateCard(): JPanel {
        emptyStatePane.apply {
            contentType = "text/html"
            isEditable = false
            isOpaque = false
            // Without this the HTMLEditorKit falls back to its serif default; honoring display
            // properties makes the HTML use the component's (IDE label) font instead.
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = JBUI.Fonts.label()
            border = JBUI.Borders.empty(8, 16)
            // Behave like a static message, not a text field: no focusable caret bar, no I-beam,
            // no text selection. The hyperlink still works (it activates on click regardless of focus).
            isFocusable = false
            cursor = Cursor.getDefaultCursor()
            highlighter = null
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "TODO Manager")
                }
            }
        }
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            // Fill horizontally so the pane gets the full width to wrap into; weighty 0 keeps it
            // vertically centered in the card.
            add(emptyStatePane, GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            })
        }
    }

    /** Wraps [body] in centered, theme-gray HTML for the empty-state pane (no fixed width → it wraps). */
    private fun emptyStateHtml(body: String): String {
        val g = JBColor.GRAY
        val gray = String.format("#%02X%02X%02X", g.red, g.green, g.blue)
        return "<html><body style='text-align:center; color:$gray'>$body</body></html>"
    }

    /**
     * Rebuilds the tree from the current scan results, grouped by the selected mode.
     */
    private fun rebuildTree() {

        // Snapshot the on-screen group state before we tear the tree down, so a refresh can restore
        // each group to how the user left it (and let brand-new groups fall back to the default).
        val priorGroups = captureGroupState()

        rootNode.removeAllChildren()
        val keywordFilter = Config.KEYWORD_FILTER
        val items = scannerService.items
            .filter { Config.SHOW_DONE || !it.done }
            .filter { keywordFilter == Config.ALL_KEYWORDS || it.keyword.equals(keywordFilter, ignoreCase = true) }
            .sortedWith(compareBy({ it.file?.path ?: "" }, { it.line }))
        val groupBy = Config.GROUP_BY

        val grouped: Map<String, List<TodoItem>> = when (groupBy) {
            "TAG" -> items.groupBy { it.tag ?: "(no tag)" }
            "PRIORITY" -> items.groupBy { it.priority?.replaceFirstChar { c -> c.uppercase() } ?: "(no priority)" }
            "KEYWORD" -> items.groupBy { it.keyword }
            else -> items.groupBy { it.file?.name ?: GENERAL_GROUP }
        }

        var sortedKeys = if (groupBy == "PRIORITY") {
            val order = listOf("Critical", "High", "Medium", "Low", "(no priority)")
            grouped.keys.sortedBy { key -> order.indexOf(key).let { if (it == -1) order.size else it } }
        } else {
            grouped.keys.sorted()
        }
        if (Config.REVERSE_SORT) sortedKeys = sortedKeys.reversed()

        for (groupName in sortedKeys) {
            var groupItems = grouped[groupName] ?: continue
            if (Config.REVERSE_SORT) groupItems = groupItems.reversed()
            val groupNode = DefaultMutableTreeNode(GroupLabel(groupName, groupItems.size))
            for (item in groupItems) {
                groupNode.add(DefaultMutableTreeNode(item))
            }
            rootNode.add(groupNode)
        }

        treeModel.reload()
        restoreGroupState(priorGroups)
        updateEmptyState(items.isEmpty())
    }

    /** Shows the tree, or a centered empty-state message (settings link when no source dir was found). */
    private fun updateEmptyState(isEmpty: Boolean) {
        if (!isEmpty) {
            centerLayout.show(centerPanel, CARD_TREE)
            return
        }
        val body = if (scannerService.noSourceDirFound) {
            "No source folder found — nothing is scanned. " +
                "<a href='settings'>Open settings</a> to add one or scan the whole project."
        } else {
            "No TODOs to show"
        }
        emptyStatePane.text = emptyStateHtml(body)
        centerLayout.show(centerPanel, CARD_EMPTY)
    }

    /** A snapshot of the group nodes that existed, and which of those were expanded, keyed by name. */
    private class GroupState(val present: Set<String>, val expanded: Set<String>)

    /** Records, per group name, whether the group was present and whether it was expanded. */
    private fun captureGroupState(): GroupState {
        val present = mutableSetOf<String>()
        val expanded = mutableSetOf<String>()
        for (i in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val name = (node.userObject as? GroupLabel)?.name ?: continue
            present.add(name)
            if (tree.isExpanded(TreePath(node.path))) expanded.add(name)
        }
        return GroupState(present, expanded)
    }

    /**
     * Restores each group to its prior expand/collapse state. Groups that didn't exist before (e.g.
     * a newly added file, or after switching the grouping mode) use [Config.COLLAPSE_BY_DEFAULT].
     */
    private fun restoreGroupState(prior: GroupState) {
        for (i in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val name = (node.userObject as? GroupLabel)?.name ?: continue
            val path = TreePath(node.path)
            val expand = when (name) {
                in prior.expanded -> true
                in prior.present -> false          // existed and was collapsed → stay collapsed
                else -> !Config.COLLAPSE_BY_DEFAULT // new group → default
            }
            if (expand) tree.expandPath(path) else tree.collapsePath(path)
        }
    }

    /** Expands every group node (used when the user turns "Collapse" off). */
    private fun expandAllGroups() {
        for (i in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            tree.expandPath(TreePath(node.path))
        }
    }

    /** Collapses every group node (used when the user turns "Collapse" on). */
    private fun collapseAllGroups() {
        for (i in 0 until rootNode.childCount) {
            val node = rootNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            tree.collapsePath(TreePath(node.path))
        }
    }

    private fun navigateToSelected() {
        val item = getSelectedItem() ?: return
        val file = item.file ?: return
        val range = item.textRange ?: return
        OpenFileDescriptor(project, file, range.startOffset).navigate(true)
    }

    private fun getSelectedItem(): TodoItem? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? TodoItem
    }

    /* ============ Context Menu ============ */

    private fun showContextMenu(e: MouseEvent) {
        val item = getSelectedItem() ?: return
        val menu = JPopupMenu()

        if (item.source == TodoSource.CODE) {
            menu.add(JMenuItem("Go to Source").apply {
                addActionListener { navigateToSelected() }
            })
            menu.addSeparator()
        }
        menu.add(JMenuItem("Edit Description...").apply {
            addActionListener { editDescription(item) }
        })
        menu.add(JMenuItem("Change Tag...").apply {
            addActionListener { editTag(item) }
        })
        menu.add(createPrioritySubMenu(item))
        menu.addSeparator()
        if (!item.done) {
            menu.add(JMenuItem("Mark as Done").apply {
                addActionListener { markDone(item) }
            })
        }
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
                        if (item.source == TodoSource.GENERAL) {
                            scannerService.updateGeneralTodo(item.generalId!!) { it.priority = p }
                        } else {
                            TodoEditor.setPriority(project, item, p)
                            refreshAfterEdit(item)
                        }
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
        if (item.source == TodoSource.GENERAL) {
            scannerService.updateGeneralTodo(item.generalId!!) { it.description = newDesc }
        } else {
            TodoEditor.setDescription(project, item, newDesc)
            refreshAfterEdit(item)
        }
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
        if (item.source == TodoSource.GENERAL) {
            scannerService.updateGeneralTodo(item.generalId!!) { it.tag = newTag.ifBlank { null } }
        } else {
            TodoEditor.setTag(project, item, newTag.ifBlank { null })
            refreshAfterEdit(item)
        }
    }

    private fun markDone(item: TodoItem) {
        if (item.source == TodoSource.GENERAL) {
            scannerService.updateGeneralTodo(item.generalId!!) {
                it.done = true
                it.doneBy = GitUser.userName(project)
                it.doneAt = java.time.LocalDate.now().toString()
            }
        } else {
            TodoEditor.markDone(project, item)
            refreshAfterEdit(item)
        }
    }

    private fun deleteTodo(item: TodoItem) {
        if (item.source == TodoSource.GENERAL) {
            val ok = Messages.showYesNoDialog(
                project,
                "Delete this general TODO?",
                "Delete TODO",
                Messages.getQuestionIcon()
            )
            if (ok == Messages.YES) {
                scannerService.removeGeneralTodo(item.generalId!!)
            }
            return
        }
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
        if (!dialog.showAndGet()) return

        val keyword = dialog.keyword
        val tag = dialog.tag.ifBlank { null }
        val priority = dialog.priority.ifBlank { null }
        val description = dialog.description

        if (dialog.isGeneral) {
            scannerService.addGeneralTodo(keyword, tag, priority, description)
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
        ApplicationManager.getApplication().executeOnPooledThread {
            scannerService.refresh()
        }
    }

    private fun refreshAfterEdit(item: TodoItem) {
        val file = item.file ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            scannerService.refreshFile(file)
        }
    }
}

/** Label for group header nodes in the tree. */
data class GroupLabel(val name: String, val count: Int)

/**
 * Custom tree cell renderer — shows colored badges for keywords and priorities.
 */
class TodoCellRenderer : DefaultTreeCellRenderer() {

    /** Off-screen label used to measure the pixel width of the struck-through portion. */
    private val measureLabel = JLabel()
    /** Pixel width of the strikethrough to draw (0 = none). Set per row in [getTreeCellRendererComponent]. */
    private var strikeWidth = 0
    private var strikeColor: java.awt.Color = java.awt.Color.GRAY

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
        strikeWidth = 0
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
                val location = if (obj.source == TodoSource.GENERAL) "general" else "${obj.file?.name}:${obj.line + 1}"
                val doneStamp = if (obj.done) "✓ done by ${obj.doneBy ?: "?"} on ${obj.doneAt ?: "?"}" else null
                val trailing = when {
                    doneStamp == null -> location
                    obj.source == TodoSource.GENERAL -> doneStamp
                    else -> "$location · $doneStamp"
                }
                val body = parts.joinToString(" ")
                text = "<html>$body <font color='#888888'>— $trailing</font></html>"
                icon = null
                // Done items get a strikethrough drawn manually (in paintComponent) so the line sits
                // at the cell's vertical center — Swing's HTML <strike> renders it low and uneven
                // across mixed bold/non-bold runs.
                if (obj.done) {
                    measureLabel.font = font
                    measureLabel.text = "<html>$body</html>"
                    strikeWidth = measureLabel.preferredSize.width
                    strikeColor = Config.descriptionColor()
                }
            }
        }

        return comp
    }

    override fun paintComponent(g: java.awt.Graphics) {
        super.paintComponent(g)
        if (strikeWidth <= 0) return
        // With no icon, text starts at the left inset; the line spans only the struck body, drawn
        // through the vertical center of the (vertically centered) text.
        val x0 = insets.left
        val y = height / 2
        g.color = strikeColor
        g.drawLine(x0, y, x0 + strikeWidth, y)
    }

    private fun colorToHex(c: java.awt.Color): String =
        String.format("#%02X%02X%02X", c.red, c.green, c.blue)
}
