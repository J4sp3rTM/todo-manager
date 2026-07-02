package io.github.j4sp3rtm.todomanager

import java.awt.Color

/** Configuration facade — reads from persisted settings (Settings > Tools > TODO Manager). */
object Config {
    private val settings get() = TodoManagerSettings.getInstance().state

    val KEYWORDS: List<String> get() = settings.keywords

    /** "ANY" (case-insensitive), "UPPER" (only upper-case), or "LOWER" (only lower-case). */
    val KEYWORD_CASE: String get() = settings.keywordCase

    /** Non-ANY case modes require exact-case matching. */
    val CASE_SENSITIVE_KEYWORDS: Boolean get() = settings.keywordCase != "ANY"

    /** Keywords adjusted to the configured case mode, for feeding into [TodoPattern]. */
    fun matchKeywords(): List<String> = KEYWORDS.map { applyKeywordCase(it) }

    /** Renders a keyword in the configured case (used when inserting/rewriting comments). */
    fun applyKeywordCase(keyword: String): String = when (settings.keywordCase) {
        "LOWER" -> keyword.lowercase()
        "UPPER" -> keyword.uppercase()
        else -> keyword
    }

    val KEYWORDS_AT_LINE_START: Boolean get() = settings.keywordsAtLineStart
    val SUPPRESS_IDE_TODO: Boolean get() = settings.suppressIdeTodoHighlighting
    val GROUP_BY: String get() = settings.groupBy

    val GROUP_BY_OPTIONS: List<String> = listOf("FILE", "TAG", "PRIORITY", "KEYWORD")

    /**
     * The canonical priority set, in sort order. Single source of truth feeding the scanner regex
     * ([TodoPattern]), the "New TODO" dialog, the context menu, the group sort order, the settings
     * color-picker rows, and the highlighter's bold rule. See [TodoManagerSettings.PRIORITIES].
     */
    val PRIORITIES: List<String> get() = TodoManagerSettings.PRIORITIES

    /** The priority whose keyword gets bold emphasis in the editor highlighter. */
    const val CRITICAL_PRIORITY = "critical"

    /** Tool window keyword filter: [ALL_KEYWORDS] (show everything) or a single upper-case keyword. */
    var KEYWORD_FILTER: String
        get() = settings.keywordFilter
        set(value) { settings.keywordFilter = value }

    const val ALL_KEYWORDS = "All"
    val ENABLED: Boolean get() = settings.enabled
    val DONE_KEYWORD: String get() = settings.doneKeyword
    var SHOW_DONE: Boolean
        get() = settings.showDoneItems
        set(value) { settings.showDoneItems = value }
    var COLLAPSE_BY_DEFAULT: Boolean
        get() = settings.collapseByDefault
        set(value) { settings.collapseByDefault = value }
    var REVERSE_SORT: Boolean
        get() = settings.reverseSort
        set(value) { settings.reverseSort = value }

    /** Row location display: "NAME", "RELATIVE", or "ABSOLUTE". See [TodoManagerSettings.State.pathDisplay]. */
    val PATH_DISPLAY: String get() = settings.pathDisplay
    /** Tooltip path display: "NAME", "RELATIVE", or "ABSOLUTE". See [TodoManagerSettings.State.tooltipPathDisplay]. */
    val TOOLTIP_PATH_DISPLAY: String get() = settings.tooltipPathDisplay
    /** Whether the hover tooltip on item rows is shown at all. */
    val SHOW_ROW_TOOLTIP: Boolean get() = settings.showRowTooltip
    val PREVIEW_ON_SINGLE_CLICK: Boolean get() = settings.previewOnSingleClick
    val BOLD_KEYWORDS: Boolean get() = settings.boldKeywords
    val UNDERLINE_TAGS: Boolean get() = settings.underlineTags

    val LIMIT_TO_SOURCE_DIRS: Boolean get() = settings.limitToSourceDirs
    val RESPECT_IDE_EXCLUDES: Boolean get() = settings.respectIdeExcludes
    val SOURCE_DIR_NAMES: List<String> get() = settings.sourceDirNames
    val EXCLUDED_DIR_NAMES: List<String> get() = settings.excludedDirNames

    fun keywordColor(keyword: String): Color {
        val hex = settings.keywordColors[keyword] ?: settings.defaultKeywordColor
        return Color.decode(hex)
    }

    fun descriptionColor(): Color = Color.decode(settings.descriptionColor)

    fun delimiterColor(): Color = Color.decode(settings.delimiterColor)

    fun priorityColor(priority: String): Color? {
        val hex = settings.priorityColors[priority] ?: return null
        return Color.decode(hex)
    }

    fun tagColor(tag: String): Color {
        // Check explicit tag override first
        val override = settings.tagColors[tag]
        if (override != null) return Color.decode(override)
        // Fall back to palette by hash
        val palette = settings.tagPalette
        if (palette.isEmpty()) return Color.GRAY
        val index = (tag.hashCode().and(0x7FFFFFFF)) % palette.size
        return Color.decode(palette[index])
    }
}
