package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

class TodoManagerConfigurable : Configurable {

    private var panel: JPanel? = null
    private var enabledCheckbox: JCheckBox? = null
    private var keywordsField: JTextField? = null
    private var groupByCombo: JComboBox<String>? = null
    private var boldKeywordsCheckbox: JCheckBox? = null
    private var underlineTagsCheckbox: JCheckBox? = null
    private var keywordCaseCombo: JComboBox<String>? = null
    private var keywordsAtLineStartCheckbox: JCheckBox? = null
    private var suppressIdeTodoCheckbox: JCheckBox? = null

    // Scanning scope
    private var limitToSourceDirsCheckbox: JCheckBox? = null
    private var respectIdeExcludesCheckbox: JCheckBox? = null
    private var sourceDirNamesField: JTextField? = null
    private var excludedDirNamesField: JTextField? = null

    // Keyword color pickers: keyword name → ColorPanel
    private var keywordColorPanels: MutableMap<String, ColorPanel> = mutableMapOf()
    private var keywordColorsContainer: JPanel? = null
    private var defaultKeywordColorPanel: ColorPanel? = null

    // Comment text / delimiter colors
    private var descriptionColorPanel: ColorPanel? = null
    private var delimiterColorPanel: ColorPanel? = null

    // Priority color pickers
    private var priorityColorPanels: MutableMap<String, ColorPanel> = mutableMapOf()

    // Tag color overrides: tag name → ColorPanel
    private var tagColorRows: MutableList<TagColorRow> = mutableListOf()
    private var tagColorsContainer: JPanel? = null

    // Tag palette color pickers
    private var tagPalettePanels: MutableList<ColorPanel> = mutableListOf()
    private var tagPaletteContainer: JPanel? = null

    private data class TagColorRow(val nameField: JTextField, val colorPanel: ColorPanel)

    override fun getDisplayName(): String = "TODO Manager"

    override fun createComponent(): JComponent {
        val state = TodoManagerSettings.getInstance().state

        enabledCheckbox = JCheckBox("Enable TODO Manager", state.enabled)
        keywordsField = JTextField(state.keywords.joinToString(", "), 30)
        groupByCombo = JComboBox(arrayOf("FILE", "TAG", "PRIORITY")).apply {
            selectedItem = state.groupBy
        }
        boldKeywordsCheckbox = JCheckBox("Bold keywords", state.boldKeywords)
        underlineTagsCheckbox = JCheckBox("Underline tags", state.underlineTags)
        keywordCaseCombo = JComboBox(KEYWORD_CASE_LABELS).apply {
            selectedItem = labelForCase(state.keywordCase)
            toolTipText = "Any case: case-insensitive. Upper/Lower-case only: a keyword in the other " +
                "case (e.g. lower-case 'note' in prose) is not picked up."
        }
        keywordsAtLineStartCheckbox = JCheckBox("Only match keyword at start of comment/line", state.keywordsAtLineStart).apply {
            toolTipText = "When on, a keyword is recognized only as the first word on its line, not mid-sentence."
        }
        suppressIdeTodoCheckbox = JCheckBox("Suppress the IDE's built-in TODO highlighting", state.suppressIdeTodoHighlighting).apply {
            toolTipText = "Clears the IDE's own todo/fixme patterns (Settings > Editor > TODO) so they " +
                "don't double up with this plugin. Restored when turned off. Also empties the IDE's TODO tool window."
        }

        limitToSourceDirsCheckbox = JCheckBox(
            "Limit scanning to detected source directories (recommended)", state.limitToSourceDirs
        )
        respectIdeExcludesCheckbox = JCheckBox(
            "Skip IDE-excluded folders and library files", state.respectIdeExcludes
        )
        sourceDirNamesField = JTextField(state.sourceDirNames.joinToString(", "), 30)
        excludedDirNamesField = JTextField(state.excludedDirNames.joinToString(", "), 30)

        // Keyword colors section
        keywordColorsContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        rebuildKeywordColorRows(state.keywordColors)

        defaultKeywordColorPanel = ColorPanel().apply {
            selectedColor = Color.decode(state.defaultKeywordColor)
        }

        descriptionColorPanel = ColorPanel().apply {
            selectedColor = Color.decode(state.descriptionColor)
        }

        delimiterColorPanel = ColorPanel().apply {
            selectedColor = Color.decode(state.delimiterColor)
        }

        // Priority colors section
        val priorityColorsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        for (prio in listOf("critical", "high", "medium", "low")) {
            val cp = ColorPanel()
            cp.selectedColor = Color.decode(state.priorityColors[prio] ?: "#9E9E9E")
            priorityColorPanels[prio] = cp
            priorityColorsPanel.add(colorRow(prio.replaceFirstChar { it.uppercase() } + ":", cp))
            priorityColorsPanel.add(Box.createVerticalStrut(4))
        }

        // Tag color overrides section
        tagColorsContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        rebuildTagColorOverrideRows(state.tagColors)

        val addTagOverrideButton = JButton("Add Tag Override").apply {
            addActionListener { addTagColorOverrideRow("", Color.WHITE) }
        }

        // Tag palette section
        tagPaletteContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        rebuildTagPaletteRows(state.tagPalette)

        val addTagColorButton = JButton("Add Color").apply {
            addActionListener { addTagPaletteRow(Color.WHITE) }
        }

        val resetButton = JButton("Reset All to Defaults").apply {
            addActionListener { resetToDefaults() }
        }

        // Sync keyword colors when keywords field changes
        keywordsField?.addActionListener { syncKeywordColorRows() }

        val syncButton = JButton("Sync Keywords").apply {
            toolTipText = "Update keyword color rows to match the keywords field"
            addActionListener { syncKeywordColorRows() }
        }

        panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            add(enabledCheckbox!!.leftAlign())
            add(vstrut())
            add(labeledRow("Keywords (comma-separated):", keywordsField!!))
            add(Box.createVerticalStrut(4))
            add(syncButton.leftAlign())
            add(vstrut())
            add(labeledRow("Group by:", groupByCombo!!))
            add(vstrut())
            add(labeledRow("Keyword case:", keywordCaseCombo!!))
            add(Box.createVerticalStrut(4))
            add(keywordsAtLineStartCheckbox!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(suppressIdeTodoCheckbox!!.leftAlign())
            add(vstrut())
            add(boldKeywordsCheckbox!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(underlineTagsCheckbox!!.leftAlign())
            add(vstrut())

            // Scanning Scope
            add(sectionLabel("Scanning Scope"))
            add(Box.createVerticalStrut(4))
            add(limitToSourceDirsCheckbox!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(respectIdeExcludesCheckbox!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(labeledRow("Source directory names:", sourceDirNamesField!!))
            add(Box.createVerticalStrut(2))
            add(JLabel("<html><i>Auto-detected as roots when present, e.g. src, app, lib.</i></html>").leftAlign())
            add(Box.createVerticalStrut(4))
            add(labeledRow("Excluded directory names:", excludedDirNamesField!!))
            add(Box.createVerticalStrut(2))
            add(JLabel("<html><i>Always skipped, e.g. node_modules, build, dist.</i></html>").leftAlign())
            add(vstrut())

            // Keyword Colors
            add(sectionLabel("Keyword Colors"))
            add(Box.createVerticalStrut(4))
            add(keywordColorsContainer!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(labeledRow("Default (other keywords):", defaultKeywordColorPanel!!))
            add(vstrut())

            // Priority Colors
            add(sectionLabel("Priority Colors"))
            add(Box.createVerticalStrut(4))
            add(priorityColorsPanel.leftAlign())
            add(vstrut())

            // Comment Colors
            add(sectionLabel("Comment Colors"))
            add(Box.createVerticalStrut(4))
            add(colorRow("Description text:", descriptionColorPanel!!))
            add(Box.createVerticalStrut(4))
            add(colorRow("Delimiters:", delimiterColorPanel!!))
            add(Box.createVerticalStrut(2))
            add(JLabel("<html><i>Comment markers such as // and /* */.</i></html>").leftAlign())
            add(vstrut())

            // Tag Color Overrides
            add(sectionLabel("Tag Color Overrides"))
            add(Box.createVerticalStrut(4))
            add(JLabel("Pin specific tags to specific colors:").leftAlign())
            add(Box.createVerticalStrut(4))
            add(tagColorsContainer!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(addTagOverrideButton.leftAlign())
            add(vstrut())

            // Tag Palette (fallback)
            add(sectionLabel("Tag Color Palette (fallback)"))
            add(Box.createVerticalStrut(4))
            add(JLabel("Colors for tags without an override, assigned by hash:").leftAlign())
            add(Box.createVerticalStrut(4))
            add(tagPaletteContainer!!.leftAlign())
            add(Box.createVerticalStrut(4))
            add(addTagColorButton.leftAlign())
            add(vstrut())

            add(resetButton.leftAlign())
            add(Box.createVerticalGlue())
        }

        return JBScrollPane(panel).apply {
            border = null
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            isWheelScrollingEnabled = true
        }
    }

    /* ============ Keyword color rows ============ */

    private fun rebuildKeywordColorRows(colorMap: Map<String, String>) {
        keywordColorsContainer?.removeAll()
        keywordColorPanels.clear()
        val keywords = keywordsField?.text
            ?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }
            ?: colorMap.keys.toList()
        for (kw in keywords) {
            val hex = colorMap[kw] ?: TodoManagerSettings.getInstance().state.defaultKeywordColor
            addKeywordColorRow(kw, Color.decode(hex))
        }
    }

    private fun addKeywordColorRow(keyword: String, color: Color) {
        val cp = ColorPanel()
        cp.selectedColor = color
        keywordColorPanels[keyword] = cp
        val row = colorRow("$keyword:", cp)
        keywordColorsContainer?.add(row)
        keywordColorsContainer?.add(Box.createVerticalStrut(4))
    }

    private fun syncKeywordColorRows() {
        val currentKeywords = keywordsField?.text
            ?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }
            ?: return
        // Preserve existing colors
        val existingColors = keywordColorPanels.mapValues { it.value.selectedColor }
        val defaults = TodoManagerSettings.defaultKeywordColors()
        val newMap = mutableMapOf<String, String>()
        for (kw in currentKeywords) {
            val color = existingColors[kw]
            val hex = if (color != null) colorToHex(color) else (defaults[kw] ?: "#9E9E9E")
            newMap[kw] = hex
        }
        rebuildKeywordColorRows(newMap)
        keywordColorsContainer?.revalidate()
        keywordColorsContainer?.repaint()
    }

    /* ============ Tag color override rows ============ */

    private fun rebuildTagColorOverrideRows(colorMap: Map<String, String>) {
        tagColorsContainer?.removeAll()
        tagColorRows.clear()
        for ((tag, hex) in colorMap) {
            addTagColorOverrideRow(tag, Color.decode(hex))
        }
    }

    private fun addTagColorOverrideRow(tag: String, color: Color) {
        val nameField = JTextField(tag, 12)
        val cp = ColorPanel()
        cp.selectedColor = color
        val rowData = TagColorRow(nameField, cp)
        tagColorRows.add(rowData)

        val removeButton = JButton("Remove").apply {
            addActionListener {
                tagColorRows.remove(rowData)
                tagColorsContainer?.let { container ->
                    val row = nameField.parent
                    container.remove(row)
                    container.revalidate()
                    container.repaint()
                }
            }
        }

        val row = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JLabel("Tag:"))
            add(Box.createHorizontalStrut(4))
            add(nameField)
            add(Box.createHorizontalStrut(8))
            add(JLabel("Color:"))
            add(Box.createHorizontalStrut(4))
            add(cp)
            add(Box.createHorizontalStrut(8))
            add(removeButton)
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        tagColorsContainer?.add(row)
        tagColorsContainer?.add(Box.createVerticalStrut(4))
        tagColorsContainer?.revalidate()
        tagColorsContainer?.repaint()
    }

    /* ============ Tag palette rows ============ */

    private fun rebuildTagPaletteRows(hexColors: List<String>) {
        tagPaletteContainer?.removeAll()
        tagPalettePanels.clear()
        for (hex in hexColors) {
            addTagPaletteRow(Color.decode(hex))
        }
    }

    private fun addTagPaletteRow(color: Color) {
        val cp = ColorPanel()
        cp.selectedColor = color
        tagPalettePanels.add(cp)

        val removeButton = JButton("Remove").apply {
            addActionListener {
                tagPalettePanels.remove(cp)
                tagPaletteContainer?.let { container ->
                    val row = cp.parent
                    container.remove(row)
                    // Remove the strut after (if present)
                    if (container.componentCount > 0) {
                        val next = container.getComponent(
                            kotlin.math.min(container.components.indexOf(row).coerceAtLeast(0), container.componentCount - 1)
                        )
                        if (next is Box.Filler) container.remove(next)
                    }
                    container.revalidate()
                    container.repaint()
                }
            }
        }

        val row = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JLabel("Color ${tagPalettePanels.size}:"))
            add(Box.createHorizontalStrut(8))
            add(cp)
            add(Box.createHorizontalStrut(8))
            add(removeButton)
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        tagPaletteContainer?.add(row)
        tagPaletteContainer?.add(Box.createVerticalStrut(4))
        tagPaletteContainer?.revalidate()
        tagPaletteContainer?.repaint()
    }

    /* ============ Configurable interface ============ */

    override fun isModified(): Boolean {
        val s = TodoManagerSettings.getInstance().state
        val currentKeywords = parseKeywords()
        if (s.enabled != enabledCheckbox?.isSelected) return true
        if (s.keywords != currentKeywords) return true
        if (s.groupBy != groupByCombo?.selectedItem) return true
        if (s.keywordCase != selectedKeywordCase()) return true
        if (s.keywordsAtLineStart != keywordsAtLineStartCheckbox?.isSelected) return true
        if (s.suppressIdeTodoHighlighting != suppressIdeTodoCheckbox?.isSelected) return true
        if (s.boldKeywords != boldKeywordsCheckbox?.isSelected) return true
        if (s.underlineTags != underlineTagsCheckbox?.isSelected) return true

        // Scanning scope
        if (s.limitToSourceDirs != limitToSourceDirsCheckbox?.isSelected) return true
        if (s.respectIdeExcludes != respectIdeExcludesCheckbox?.isSelected) return true
        if (s.sourceDirNames != parseCsv(sourceDirNamesField)) return true
        if (s.excludedDirNames != parseCsv(excludedDirNamesField)) return true

        // Keyword colors
        val currentKwColors = keywordColorPanels.mapValues { colorToHex(it.value.selectedColor ?: Color.GRAY) }
        if (s.keywordColors != currentKwColors) return true
        if (s.defaultKeywordColor != colorToHex(defaultKeywordColorPanel?.selectedColor ?: Color.GRAY)) return true
        if (s.descriptionColor != colorToHex(descriptionColorPanel?.selectedColor ?: Color.GRAY)) return true
        if (s.delimiterColor != colorToHex(delimiterColorPanel?.selectedColor ?: Color.GRAY)) return true

        // Priority colors
        for ((prio, cp) in priorityColorPanels) {
            if (s.priorityColors[prio] != colorToHex(cp.selectedColor ?: Color.GRAY)) return true
        }

        // Tag color overrides
        val currentTagColors = tagColorRows
            .filter { it.nameField.text.isNotBlank() }
            .associate { it.nameField.text.trim() to colorToHex(it.colorPanel.selectedColor ?: Color.GRAY) }
        if (s.tagColors != currentTagColors) return true

        // Tag palette
        val currentPalette = tagPalettePanels.mapNotNull { it.selectedColor }.map { colorToHex(it) }
        if (s.tagPalette != currentPalette) return true

        return false
    }

    override fun apply() {
        val s = TodoManagerSettings.getInstance().state
        s.enabled = enabledCheckbox?.isSelected ?: s.enabled
        s.keywords = parseKeywords().toMutableList()
        s.groupBy = groupByCombo?.selectedItem as? String ?: s.groupBy
        s.keywordCase = selectedKeywordCase()
        s.keywordsAtLineStart = keywordsAtLineStartCheckbox?.isSelected ?: s.keywordsAtLineStart
        s.suppressIdeTodoHighlighting = suppressIdeTodoCheckbox?.isSelected ?: s.suppressIdeTodoHighlighting
        s.boldKeywords = boldKeywordsCheckbox?.isSelected ?: s.boldKeywords
        s.underlineTags = underlineTagsCheckbox?.isSelected ?: s.underlineTags
        s.limitToSourceDirs = limitToSourceDirsCheckbox?.isSelected ?: s.limitToSourceDirs
        s.respectIdeExcludes = respectIdeExcludesCheckbox?.isSelected ?: s.respectIdeExcludes
        s.sourceDirNames = parseCsv(sourceDirNamesField).toMutableList()
        s.excludedDirNames = parseCsv(excludedDirNamesField).toMutableList()
        s.keywordColors = keywordColorPanels.mapValues {
            colorToHex(it.value.selectedColor ?: Color.GRAY)
        }.toMutableMap()
        s.defaultKeywordColor = colorToHex(defaultKeywordColorPanel?.selectedColor ?: Color.GRAY)
        s.descriptionColor = colorToHex(descriptionColorPanel?.selectedColor ?: Color.GRAY)
        s.delimiterColor = colorToHex(delimiterColorPanel?.selectedColor ?: Color.GRAY)
        s.priorityColors = priorityColorPanels.mapValues {
            colorToHex(it.value.selectedColor ?: Color.GRAY)
        }.toMutableMap()
        s.tagColors = tagColorRows
            .filter { it.nameField.text.isNotBlank() }
            .associate { it.nameField.text.trim() to colorToHex(it.colorPanel.selectedColor ?: Color.GRAY) }
            .toMutableMap()
        s.tagPalette = tagPalettePanels.mapNotNull { it.selectedColor }.map { colorToHex(it) }.toMutableList()

        // Apply (or undo) IDE built-in TODO suppression to match the saved setting.
        IdeTodoSuppressor.sync()

        // Repaint editor highlighting and rescan in all open projects so changes apply immediately
        for (project in ProjectManager.getInstance().openProjects) {
            TodoHighlightPainter.refreshAll(project)
            ApplicationManager.getApplication().executeOnPooledThread {
                TodoScannerService.getInstance(project).refresh()
            }
        }
    }

    override fun reset() {
        val s = TodoManagerSettings.getInstance().state
        enabledCheckbox?.isSelected = s.enabled
        keywordsField?.text = s.keywords.joinToString(", ")
        groupByCombo?.selectedItem = s.groupBy
        keywordCaseCombo?.selectedItem = labelForCase(s.keywordCase)
        keywordsAtLineStartCheckbox?.isSelected = s.keywordsAtLineStart
        suppressIdeTodoCheckbox?.isSelected = s.suppressIdeTodoHighlighting
        boldKeywordsCheckbox?.isSelected = s.boldKeywords
        underlineTagsCheckbox?.isSelected = s.underlineTags
        limitToSourceDirsCheckbox?.isSelected = s.limitToSourceDirs
        respectIdeExcludesCheckbox?.isSelected = s.respectIdeExcludes
        sourceDirNamesField?.text = s.sourceDirNames.joinToString(", ")
        excludedDirNamesField?.text = s.excludedDirNames.joinToString(", ")
        rebuildKeywordColorRows(s.keywordColors)
        defaultKeywordColorPanel?.selectedColor = Color.decode(s.defaultKeywordColor)
        descriptionColorPanel?.selectedColor = Color.decode(s.descriptionColor)
        delimiterColorPanel?.selectedColor = Color.decode(s.delimiterColor)
        for ((prio, cp) in priorityColorPanels) {
            cp.selectedColor = Color.decode(s.priorityColors[prio] ?: "#9E9E9E")
        }
        rebuildTagColorOverrideRows(s.tagColors)
        rebuildTagPaletteRows(s.tagPalette)
    }

    override fun disposeUIResources() {
        panel = null
        enabledCheckbox = null
        keywordsField = null
        groupByCombo = null
        boldKeywordsCheckbox = null
        underlineTagsCheckbox = null
        keywordCaseCombo = null
        keywordsAtLineStartCheckbox = null
        suppressIdeTodoCheckbox = null
        limitToSourceDirsCheckbox = null
        respectIdeExcludesCheckbox = null
        sourceDirNamesField = null
        excludedDirNamesField = null
        keywordColorPanels.clear()
        keywordColorsContainer = null
        defaultKeywordColorPanel = null
        descriptionColorPanel = null
        delimiterColorPanel = null
        priorityColorPanels.clear()
        tagColorRows.clear()
        tagColorsContainer = null
        tagPalettePanels.clear()
        tagPaletteContainer = null
    }

    /* ============ Helpers ============ */

    private fun resetToDefaults() {
        val defaults = TodoManagerSettings.State()
        enabledCheckbox?.isSelected = defaults.enabled
        keywordsField?.text = defaults.keywords.joinToString(", ")
        groupByCombo?.selectedItem = defaults.groupBy
        keywordCaseCombo?.selectedItem = labelForCase(defaults.keywordCase)
        keywordsAtLineStartCheckbox?.isSelected = defaults.keywordsAtLineStart
        suppressIdeTodoCheckbox?.isSelected = defaults.suppressIdeTodoHighlighting
        boldKeywordsCheckbox?.isSelected = defaults.boldKeywords
        underlineTagsCheckbox?.isSelected = defaults.underlineTags
        limitToSourceDirsCheckbox?.isSelected = defaults.limitToSourceDirs
        respectIdeExcludesCheckbox?.isSelected = defaults.respectIdeExcludes
        sourceDirNamesField?.text = defaults.sourceDirNames.joinToString(", ")
        excludedDirNamesField?.text = defaults.excludedDirNames.joinToString(", ")
        rebuildKeywordColorRows(defaults.keywordColors)
        defaultKeywordColorPanel?.selectedColor = Color.decode(defaults.defaultKeywordColor)
        descriptionColorPanel?.selectedColor = Color.decode(defaults.descriptionColor)
        delimiterColorPanel?.selectedColor = Color.decode(defaults.delimiterColor)
        for ((prio, cp) in priorityColorPanels) {
            cp.selectedColor = Color.decode(defaults.priorityColors[prio] ?: "#9E9E9E")
        }
        rebuildTagColorOverrideRows(defaults.tagColors)
        rebuildTagPaletteRows(defaults.tagPalette)
        keywordColorsContainer?.revalidate()
        tagColorsContainer?.revalidate()
        tagPaletteContainer?.revalidate()
        panel?.repaint()
    }

    /** Currently selected keyword-case mode as a stored code ("ANY"/"UPPER"/"LOWER"). */
    private fun selectedKeywordCase(): String =
        caseForLabel(keywordCaseCombo?.selectedItem as? String)

    private fun parseKeywords(): List<String> =
        keywordsField?.text?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun parseCsv(field: JTextField?): MutableList<String> =
        field?.text?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()

    private fun colorRow(label: String, cp: ColorPanel): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JLabel(label).apply { preferredSize = Dimension(100, preferredSize.height) })
            add(Box.createHorizontalStrut(8))
            add(cp)
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun labeledRow(label: String, component: JComponent): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JLabel(label))
            add(Box.createHorizontalStrut(8))
            add(component)
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun sectionLabel(text: String): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = JPanel.LEFT_ALIGNMENT
            add(JSeparator().apply { maximumSize = Dimension(20, 2) })
            add(Box.createHorizontalStrut(6))
            add(JLabel("<html><b>$text</b></html>"))
            add(Box.createHorizontalStrut(6))
            add(JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 2) })
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun vstrut() = Box.createVerticalStrut(12)

    private fun JComponent.leftAlign(): JComponent {
        alignmentX = JPanel.LEFT_ALIGNMENT
        return this
    }

    private fun colorToHex(c: Color): String =
        String.format("#%02X%02X%02X", c.red, c.green, c.blue)

    private companion object {
        val KEYWORD_CASE_LABELS = arrayOf("Any case", "Upper-case only", "Lower-case only")

        fun labelForCase(code: String): String = when (code) {
            "UPPER" -> "Upper-case only"
            "LOWER" -> "Lower-case only"
            else -> "Any case"
        }

        fun caseForLabel(label: String?): String = when (label) {
            "Upper-case only" -> "UPPER"
            "Lower-case only" -> "LOWER"
            else -> "ANY"
        }
    }
}
