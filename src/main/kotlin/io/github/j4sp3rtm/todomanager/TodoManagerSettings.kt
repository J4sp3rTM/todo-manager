package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.XCollection

@Service(Service.Level.APP)
@State(name = "TodoManagerSettings", storages = [Storage("TodoManagerSettings.xml")])
class TodoManagerSettings : PersistentStateComponent<TodoManagerSettings.State> {

    data class State(
        @XCollection(style = XCollection.Style.v2)
        var keywords: MutableList<String> = mutableListOf("TODO", "FIXME", "HACK", "NOTE", "XXX"),
        var groupBy: String = "FILE",

        /** Tool window keyword filter: "All", or a single keyword (upper-case) to show exclusively. */
        var keywordFilter: String = "All",
        var enabled: Boolean = true,

        /**
         * How keyword casing is matched: "ANY" (case-insensitive, default), "UPPER" (only
         * upper-case, e.g. `NOTE`), or "LOWER" (only lower-case, e.g. `note`). The non-ANY modes
         * stop a keyword written in the other case from being picked up.
         */
        var keywordCase: String = "ANY",

        /** When true, a keyword is only recognized as the first word on its line, not mid-sentence. */
        var keywordsAtLineStart: Boolean = false,

        /**
         * When true, the IDE's own built-in TODO highlighting (Settings > Editor > TODO) is cleared
         * so it doesn't double up with this plugin. The original patterns are stashed in
         * [savedIdeTodoPatterns] so they can be restored when this is turned back off.
         */
        var suppressIdeTodoHighlighting: Boolean = false,

        /** Backup of the IDE's TODO patterns while suppressed, encoded as "<T|F><regex>". */
        @XCollection(style = XCollection.Style.v2)
        var savedIdeTodoPatterns: MutableList<String> = mutableListOf(),

        /** Keyword that marks a TODO as completed (written by "Mark as Done", scanned back as done). */
        var doneKeyword: String = "DONE",

        /** Whether completed (DONE) items are shown in the tool window. Toggled from the toolbar. */
        var showDoneItems: Boolean = true,

        /**
         * When true, group nodes in the tool window start collapsed. Newly appearing groups on a
         * refresh also start collapsed (existing groups keep whatever state the user left them in).
         */
        var collapseByDefault: Boolean = true,

        /** When true, the tool window's group order and the items within each group are reversed. */
        var reverseSort: Boolean = false,

        /** Keyword → hex color, e.g. "TODO" → "#42A5F5" */
        @MapAnnotation(surroundWithTag = false)
        var keywordColors: MutableMap<String, String> = defaultKeywordColors(),

        /** Default color for keywords not in the map */
        var defaultKeywordColor: String = "#9E9E9E",

        /** Color for the TODO description text (the free-text part after keyword/tag/priority). */
        var descriptionColor: String = "#808080",

        /** Color for the comment delimiters themselves, e.g. "/*" … "*/", "//", "<!--" … "-->". */
        var delimiterColor: String = "#6A737D",

        /** Priority → hex color, e.g. "critical" → "#D32F2F" */
        @MapAnnotation(surroundWithTag = false)
        var priorityColors: MutableMap<String, String> = defaultPriorityColors(),

        /** Tag color palette (cycled by hash of tag name — fallback) */
        @XCollection(style = XCollection.Style.v2)
        var tagPalette: MutableList<String> = defaultTagPalette(),

        /** Tag → hex color overrides, e.g. "SECURITY" → "#E53935". Takes priority over palette. */
        @MapAnnotation(surroundWithTag = false)
        var tagColors: MutableMap<String, String> = mutableMapOf(),

        /** Whether to bold keywords in the editor */
        var boldKeywords: Boolean = true,

        /** Whether to underline tags in the editor */
        var underlineTags: Boolean = true,

        /* ============ Scanning scope ============ */

        /**
         * When true, scanning is restricted to detected source directories (see [sourceDirNames])
         * when any exist under a content root; otherwise the whole content root is scanned.
         *
         * Off by default: scanning the whole content root (minus excluded/junk folders and IDE
         * excludes) covers projects whose code lives in arbitrarily named folders, so a parent and
         * all of its sub-folders are picked up without having to register each as a source root.
         */
        var limitToSourceDirs: Boolean = false,

        /** When true, skip folders the IDE marks excluded and files belonging to libraries. */
        var respectIdeExcludes: Boolean = true,

        /** Directory names auto-detected as source roots, e.g. "src", "app". Case-insensitive. */
        @XCollection(style = XCollection.Style.v2)
        var sourceDirNames: MutableList<String> = defaultSourceDirNames(),

        /** Directory names always skipped, e.g. "node_modules", "build". Case-insensitive. */
        @XCollection(style = XCollection.Style.v2)
        var excludedDirNames: MutableList<String> = defaultExcludedDirNames(),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        @JvmStatic
        fun getInstance(): TodoManagerSettings =
            ApplicationManager.getApplication().getService(TodoManagerSettings::class.java)

        fun defaultKeywordColors(): MutableMap<String, String> = mutableMapOf(
            "TODO" to "#42A5F5",
            "FIXME" to "#E53935",
            "HACK" to "#FF9800",
            "NOTE" to "#00ACC1",
            "XXX" to "#AB47BC",
        )

        fun defaultPriorityColors(): MutableMap<String, String> = mutableMapOf(
            "critical" to "#D32F2F",
            "high" to "#E53935",
            "medium" to "#FFB300",
            "low" to "#4CAF50",
        )

        fun defaultTagPalette(): MutableList<String> = mutableListOf(
            "#42A5F5", "#AB47BC", "#00ACC1", "#FF9800",
            "#7E57C2", "#EC407A", "#26A69A", "#FFB74D",
        )

        fun defaultSourceDirNames(): MutableList<String> = mutableListOf(
            "src", "source", "sources", "app", "lib", "main", "test", "tests",
        )

        fun defaultExcludedDirNames(): MutableList<String> = mutableListOf(
            "node_modules", ".git", ".idea", ".gradle", "build", "dist", "out",
            "target", "bin", "obj", "vendor", "venv", ".venv", "__pycache__",
            ".next", ".nuxt", "coverage", ".cache",
        )
    }
}
