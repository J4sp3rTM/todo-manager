package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.TextRange

/** Where a [TodoItem] comes from: a source-code comment, or a free-standing general todo. */
enum class TodoSource { CODE, GENERAL }

/**
 * Represents a single TODO item — either found in source code (a comment) or created as a
 * general, code-free todo (see [GeneralTodoStore]).
 *
 * For [TodoSource.GENERAL] items there is no backing comment, so [file], [textRange], and
 * [matchRange] are null and [generalId] identifies the stored entry instead.
 */
data class TodoItem(
    /** The keyword that matched, canonicalized to upper case (TODO, FIXME, NOTE, …) for display/color. */
    val keyword: String,
    /** Optional tag in [brackets], e.g. "auth", "perf". Null if absent. */
    val tag: String?,
    /** Optional priority in (parentheses): "high", "medium", "low". Null if absent. */
    val priority: String?,
    /** The description text after keyword/tag/priority. */
    val description: String,
    /** The file containing this TODO. Null for general (code-free) todos. */
    val file: VirtualFile?,
    /** 0-based line number in the file. 0 for general todos. */
    val line: Int,
    /** Text range of the entire comment element (for navigation). Null for general todos. */
    val textRange: TextRange?,
    /** Exact range of the TODO content (KEYWORD through end of description) for write-back. Null for general todos. */
    val matchRange: TextRange?,
    /** The matched TODO text (e.g. "TODO [auth] (high) fix login"). */
    val originalText: String,
    /** Whether the comment is a block comment vs line comment. */
    val isBlockComment: Boolean,
    /** Whether this item is a code comment or a general (code-free) todo. */
    val source: TodoSource = TodoSource.CODE,
    /** Identifier of the backing entry in [GeneralTodoStore]; null for code comments. */
    val generalId: String? = null,
    /** Whether a general todo has been marked done. Code todos disappear when done, so this stays false. */
    val done: Boolean = false,
    /** Git/OS user who marked a general todo done. */
    val doneBy: String? = null,
    /** Date a general todo was marked done (yyyy-MM-dd). */
    val doneAt: String? = null,
    /** The keyword exactly as written in the source, so edits preserve its casing. Defaults to [keyword]. */
    val matchedKeyword: String = keyword,
)
