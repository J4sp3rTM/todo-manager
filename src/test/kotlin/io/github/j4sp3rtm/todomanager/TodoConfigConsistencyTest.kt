package io.github.j4sp3rtm.todomanager

import junit.framework.TestCase
import java.awt.Color

/**
 * Guards the single sources of truth introduced to stop hardcoded lists from drifting apart:
 * the group-by options, the priority set, and the default color maps derived from them.
 *
 * Pure (no IDE fixture): it only touches plain constants and companion helpers that don't reach
 * for the settings service, so it runs fast and catches drift at the data layer.
 */
class TodoConfigConsistencyTest : TestCase() {

    fun testGroupByOptionsCoverEveryGrouping() {
        // The toolbar and the settings dropdown both render Config.GROUP_BY_OPTIONS; every grouping
        // the tool window implements must be listed here or Settings can't select it (and applying
        // Settings would silently revert an unlisted grouping such as KEYWORD).
        assertTrue(
            "GROUP_BY_OPTIONS is missing a grouping: ${Config.GROUP_BY_OPTIONS}",
            Config.GROUP_BY_OPTIONS.containsAll(listOf("FILE", "TAG", "PRIORITY", "KEYWORD"))
        )
    }

    fun testConfigPrioritiesDelegateToTheSettingsSource() {
        assertEquals(TodoManagerSettings.PRIORITIES, Config.PRIORITIES)
    }

    fun testPrioritiesAreNonEmptyAndDistinct() {
        val priorities = TodoManagerSettings.PRIORITIES
        assertFalse("PRIORITIES must not be empty", priorities.isEmpty())
        assertEquals("PRIORITIES must not contain duplicates", priorities.size, priorities.toSet().size)
    }

    fun testCriticalPriorityIsAnActualPriority() {
        // The highlighter bolds CRITICAL_PRIORITY; if it isn't a real priority the bold rule is dead.
        assertTrue(Config.PRIORITIES.contains(Config.CRITICAL_PRIORITY))
    }

    fun testEveryPriorityHasAValidDefaultColor() {
        val colors = TodoManagerSettings.defaultPriorityColors()
        // Catches the zip() truncation trap: adding a priority without a matching color code would
        // otherwise silently leave the new priority with no default color.
        assertEquals(
            "Each priority needs exactly one default color",
            TodoManagerSettings.PRIORITIES.toSet(),
            colors.keys
        )
        colors.values.forEach { hex -> Color.decode(hex) } // throws on an invalid hex
    }

    fun testEveryDefaultKeywordHasADefaultColor() {
        val defaultKeywords = TodoManagerSettings.State().keywords
        val colors = TodoManagerSettings.defaultKeywordColors()
        for (keyword in defaultKeywords) {
            assertTrue("Default keyword '$keyword' has no default color", colors.containsKey(keyword))
        }
    }
}
