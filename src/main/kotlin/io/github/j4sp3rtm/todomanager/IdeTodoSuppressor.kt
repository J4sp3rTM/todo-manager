package io.github.j4sp3rtm.todomanager

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern

/**
 * Reconciles the IDE's built-in TODO highlighting (Settings > Editor > TODO) with the plugin's
 * "suppress IDE TODO highlighting" setting.
 *
 * The IDE paints its own greenish highlighting for the patterns configured in [TodoConfiguration]
 * (by default `todo`/`fixme`, case-insensitive, to end of line) — independently of this plugin and
 * unaware of its case / line-start rules. When suppression is on, we clear those patterns so the
 * plugin is the single source of TODO highlighting, stashing the originals so they can be restored.
 *
 * [TodoConfiguration] is an application-level service, so this affects every project; calls must run
 * on the EDT (its setters fire listeners / schedule re-indexing).
 */
object IdeTodoSuppressor {

    /**
     * Apply or undo suppression so the IDE's TODO patterns match the current setting.
     * Idempotent — safe to call on every startup and after each settings change.
     */
    fun sync() {
        val config = TodoConfiguration.getInstance()
        val settings = TodoManagerSettings.getInstance().state

        if (Config.SUPPRESS_IDE_TODO) {
            val current = config.todoPatterns
            // Only back up the real patterns — never overwrite a good backup with the empty set we
            // ourselves installed (e.g. if sync runs twice).
            if (current.isNotEmpty()) {
                settings.savedIdeTodoPatterns = current.map { encode(it) }.toMutableList()
                config.todoPatterns = emptyArray()
            }
        } else if (settings.savedIdeTodoPatterns.isNotEmpty()) {
            config.todoPatterns = settings.savedIdeTodoPatterns.map { decode(it) }.toTypedArray()
            settings.savedIdeTodoPatterns = mutableListOf()
        }
    }

    /** Encodes a pattern as "<T|F><regex>", capturing its case sensitivity and regex source. */
    private fun encode(pattern: TodoPattern): String =
        (if (pattern.isCaseSensitive) "T" else "F") + (pattern.patternString ?: "")

    /** Rebuilds a pattern from [encode]'s form, with default TODO colors (custom colors aren't kept). */
    private fun decode(encoded: String): TodoPattern {
        val caseSensitive = encoded.startsWith("T")
        val regex = encoded.drop(1)
        return TodoPattern(regex, TodoAttributesUtil.createDefault(), caseSensitive)
    }
}
