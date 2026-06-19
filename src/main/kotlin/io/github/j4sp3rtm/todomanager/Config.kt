package io.github.j4sp3rtm.todomanager

import java.awt.Color

/** Configuration facade — reads from persisted settings (Settings > Tools > TODO Manager). */
object Config {
    private val settings get() = TodoManagerSettings.getInstance().state

    val KEYWORDS: List<String> get() = settings.keywords
    val GROUP_BY: String get() = settings.groupBy
    val ENABLED: Boolean get() = settings.enabled
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
