package io.github.j4sp3rtm.todomanager

/**
 * Builds the regex used to recognize structured TODO comments.
 *
 * Format: `KEYWORD [tag] (priority) description`, where tag and priority are optional.
 * Capture groups: 1 = keyword, 2 = tag, 3 = priority, 4 = description.
 *
 * Shared by [TodoScanner] (project scan) and [TodoHighlightPainter] (editor highlighting)
 * so both stay in sync.
 */
object TodoPattern {

    fun build(keywords: List<String>): Regex {
        val keywordAlternation = keywords.joinToString("|") { Regex.escape(it) }
        return Regex(
            """\b($keywordAlternation)\b\s*(?:\[(\w[\w\s-]*)\])?\s*(?:\((critical|high|medium|low)\))?\s*[:\-–]?\s*(.*)""",
            RegexOption.IGNORE_CASE
        )
    }
}
