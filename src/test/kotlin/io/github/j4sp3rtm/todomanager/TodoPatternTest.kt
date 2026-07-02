package io.github.j4sp3rtm.todomanager

import junit.framework.TestCase

/**
 * Covers the two opt-in matching modes added for issue #1 (false positives on lower-case or
 * mid-sentence keywords): case-sensitive matching and "first word on the line" matching.
 *
 * Exercises [TodoPattern] directly so the rules are pinned independently of PSI/editor plumbing.
 */
class TodoPatternTest : TestCase() {

    private val keywords = listOf("TODO", "NOTE", "FIXME")

    /** The keyword captured by group 1, or null if the text doesn't match. */
    private fun matchedKeyword(
        text: String,
        caseSensitive: Boolean = false,
        atLineStart: Boolean = false,
    ): String? =
        TodoPattern.build(keywords, caseSensitive, atLineStart).find(text)?.groupValues?.get(1)

    /* ============ Default (backward-compatible) behavior ============ */

    fun testDefaultMatchesAnyCaseAnywhere() {
        // The reporter's case: lower-case "note" inside prose is picked up by default.
        assertEquals("note", matchedKeyword("// the credit note line item"))
        assertEquals("TODO", matchedKeyword("// please TODO this later"))
    }

    /* ============ Case-sensitive ============ */

    fun testCaseSensitiveIgnoresLowerCase() {
        assertNull(matchedKeyword("// the credit note line item", caseSensitive = true))
    }

    fun testCaseSensitiveStillMatchesUpperCase() {
        assertEquals("NOTE", matchedKeyword("// NOTE check this", caseSensitive = true))
    }

    /* ============ First word on the line ============ */

    fun testAtLineStartIgnoresMidSentenceKeyword() {
        // Even upper-case, a keyword that isn't the first word is ignored.
        assertNull(matchedKeyword("// the credit NOTE line item", atLineStart = true))
    }

    fun testAtLineStartMatchesAfterCommentDelimiters() {
        assertEquals("TODO", matchedKeyword("// TODO fix this", atLineStart = true))
        assertEquals("NOTE", matchedKeyword(" * NOTE javadoc style", atLineStart = true))
        assertEquals("FIXME", matchedKeyword("<!-- FIXME markup -->", atLineStart = true))
    }

    fun testAtLineStartMatchesPerLineInBlockComment() {
        val block = "/* heading\n   TODO wired up\n */"
        assertEquals("TODO", matchedKeyword(block, atLineStart = true))
    }

    /* ============ Lower-case only ============ */

    // The LOWER mode lower-cases the keyword list before building the pattern (see Config.matchKeywords),
    // then matches case-sensitively — so only lower-case keywords are recognized.
    private val lowerKeywords = keywords.map { it.lowercase() }

    fun testLowerOnlyMatchesLowerCase() {
        val kw = TodoPattern.build(lowerKeywords, caseSensitive = true).find("// todo fix this")?.groupValues?.get(1)
        assertEquals("todo", kw)
    }

    fun testLowerOnlyIgnoresUpperCase() {
        assertNull(TodoPattern.build(lowerKeywords, caseSensitive = true).find("// TODO fix this"))
    }

    /* ============ Combined ============ */

    fun testBothTogetherRejectLowerCaseAndMidSentence() {
        assertNull(matchedKeyword("// the credit note line", caseSensitive = true, atLineStart = true))
        assertEquals("TODO", matchedKeyword("// TODO real one", caseSensitive = true, atLineStart = true))
    }

    /* ============ Priority group (issue #4: single-source priorities) ============ */

    private val priorities = TodoManagerSettings.PRIORITIES

    /** The priority captured by group 3, or null if the optional group didn't match. */
    private fun matchedPriority(text: String, priorities: List<String> = this.priorities): String? =
        TodoPattern.build(keywords, priorities = priorities).find(text)?.groupValues?.get(3)?.ifEmpty { null }

    fun testEachCanonicalPriorityIsCaptured() {
        for (p in priorities) {
            assertEquals("'($p)' should be captured as a priority", p, matchedPriority("// TODO ($p) desc"))
        }
    }

    fun testPriorityMatchingIsCaseInsensitiveByDefault() {
        // The regex matches regardless of case (the scanner/highlighter lower-case it afterward).
        assertEquals("HIGH", matchedPriority("// TODO (HIGH) desc"))
    }

    fun testUnknownPriorityIsNotCaptured() {
        // A word that isn't a configured priority stays in the description instead.
        assertNull(matchedPriority("// TODO (urgent) desc"))
    }

    fun testPriorityAlternationFollowsTheSuppliedList() {
        // Proves the group is driven by the passed list (single source), not a hardcoded alternation:
        // extend the list and the new value is recognized.
        assertEquals("urgent", matchedPriority("// TODO (urgent) desc", priorities + "urgent"))
    }

    fun testTagPriorityAndDescriptionAreCapturedTogether() {
        val groups = TodoPattern.build(keywords, priorities = priorities)
            .find("// TODO [auth] (high) fix login")!!.groupValues
        assertEquals("TODO", groups[1])
        assertEquals("auth", groups[2])
        assertEquals("high", groups[3])
        assertEquals("fix login", groups[4].trim())
    }
}
