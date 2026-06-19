package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.TextRange

/**
 * Represents a single TODO item found in source code.
 */
data class TodoItem(
    /** The keyword that matched (TODO, FIXME, HACK, NOTE, etc.) */
    val keyword: String,
    /** Optional tag in [brackets], e.g. "auth", "perf". Null if absent. */
    val tag: String?,
    /** Optional priority in (parentheses): "high", "medium", "low". Null if absent. */
    val priority: String?,
    /** The description text after keyword/tag/priority. */
    val description: String,
    /** The file containing this TODO. */
    val file: VirtualFile,
    /** 0-based line number in the file. */
    val line: Int,
    /** Text range of the entire comment element (for navigation). */
    val textRange: TextRange,
    /** Exact range of the TODO content (KEYWORD through end of description) for write-back. */
    val matchRange: TextRange,
    /** The matched TODO text (e.g. "TODO [auth] (high) fix login"). */
    val originalText: String,
    /** Whether the comment is a block comment vs line comment. */
    val isBlockComment: Boolean,
)
