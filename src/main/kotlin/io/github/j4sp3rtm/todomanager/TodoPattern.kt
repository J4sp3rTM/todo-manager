package io.github.j4sp3rtm.todomanager

/**
 * Builds the regex used to recognize structured TODO comments.
 *
 * Format: `KEYWORD [tag] (priority) description`, where tag and priority are optional.
 * Capture groups: 1 = keyword, 2 = tag, 3 = priority, 4 = description.
 *
 * Shared by [TodoScanner] (project scan) and [TodoHighlightPainter] (editor highlighting)
 * so both stay in sync.
 *
 * @param caseSensitive when true, keywords match only with the exact (configured, upper-case)
 *        casing, so a lower-case `note` in prose is not mistaken for the `NOTE` keyword.
 * @param atLineStart when true, a keyword is only recognized when it is the first word on its
 *        line — i.e. preceded only by whitespace and comment punctuation (`//`, `*`, `<!--`, …).
 *        This keeps keywords appearing mid-sentence from being picked up.
 */
object TodoPattern {

    fun build(
        keywords: List<String>,
        caseSensitive: Boolean = false,
        atLineStart: Boolean = false,
    ): Regex {
        val keywordAlternation = keywords.joinToString("|") { Regex.escape(it) }
        // At line start: multiline-anchor the match and skip leading non-alphanumeric runs
        // (whitespace plus comment markers) so the keyword must be the first word on its line.
        // Otherwise a plain word boundary lets the keyword appear anywhere in the comment.
        val anchor = if (atLineStart) "(?m)^[^\\p{L}\\p{N}\\n]*" else "\\b"
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(
            """$anchor($keywordAlternation)\b\s*(?:\[(\w[\w\s-]*)\])?\s*(?:\((critical|high|medium|low)\))?\s*[:\-–]?\s*(.*)""",
            options
        )
    }
}
