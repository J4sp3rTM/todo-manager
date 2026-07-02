package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import java.awt.Font

/**
 * Paints TODO highlighting directly into an editor's markup model.
 *
 * Unlike an [com.intellij.lang.annotation.Annotator], this does not depend on the daemon's
 * code-insight pass — which the IDE disables for files outside a configured source root / SDK
 * (notably Java and Kotlin files in loose folders). Painting into the editor markup model makes
 * the highlighting appear for every language in any file, regardless of project configuration.
 *
 * All methods must be called on the EDT.
 */
object TodoHighlightPainter {

    private val OURS = Key.create<MutableList<RangeHighlighter>>("todomanager.highlighters")

    /** Recompute and repaint highlighting for [editor]. */
    fun refresh(editor: Editor) {
        if (editor.isDisposed) return
        val project = editor.project ?: return
        if (project.isDisposed) return

        clear(editor)
        if (!Config.ENABLED) return

        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return

        val ranges = mutableListOf<Pair<TextRange, TextAttributes>>()
        psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiComment) ranges += rangesFor(element)
                super.visitElement(element)
            }
        })

        val length = document.textLength
        val markup = editor.markupModel
        val added = ranges.mapNotNull { (range, attrs) ->
            if (range.startOffset in 0..length && range.endOffset in range.startOffset..length && !range.isEmpty) {
                markup.addRangeHighlighter(
                    range.startOffset, range.endOffset,
                    HighlighterLayer.ADDITIONAL_SYNTAX, attrs, HighlighterTargetArea.EXACT_RANGE
                )
            } else null
        }
        editor.putUserData(OURS, added.toMutableList())
    }

    /** Remove the highlighters this painter previously added to [editor]. */
    fun clear(editor: Editor) {
        val existing = editor.getUserData(OURS) ?: return
        val markup = editor.markupModel
        existing.forEach { if (it.isValid) markup.removeHighlighter(it) }
        editor.putUserData(OURS, null)
    }

    /** Repaint every open editor belonging to [project] (e.g. after a settings change). */
    fun refreshAll(project: Project) {
        EditorFactory.getInstance().allEditors
            .filter { it.project == project }
            .forEach { refresh(it) }
    }

    /** The colored sub-ranges of a single comment: keyword, tag, priority, description, delimiters. */
    private fun rangesFor(comment: PsiComment): List<Pair<TextRange, TextAttributes>> {
        val text = comment.text
        val matches = TodoPattern.build(
            // Match the same set the scanner does (user keywords + DONE) so completed items are
            // highlighted in the editor too, not just listed in the tool window. This honors the
            // TodoPattern doc's claim that the scanner and highlighter stay in sync.
            Config.matchKeywords() + Config.DONE_KEYWORD,
            caseSensitive = Config.CASE_SENSITIVE_KEYWORDS,
            atLineStart = Config.KEYWORDS_AT_LINE_START,
            priorities = Config.PRIORITIES,
        ).findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<TextRange, TextAttributes>>()
        val start = comment.textRange.startOffset
        val end = comment.textRange.endOffset

        // Comment delimiters (e.g. "/*" … "*/", "//", "<!--" … "-->").
        val (prefix, suffix) = CommentDelimiters.of(comment)
        val delimiterAttrs = TextAttributes().apply { foregroundColor = Config.delimiterColor() }
        if (prefix != null) result += TextRange(start, start + prefix.length) to delimiterAttrs
        if (suffix != null) result += TextRange(end - suffix.length, end) to delimiterAttrs
        val descLimit = if (suffix != null) end - suffix.length else end

        for (match in matches) {
            // Keyword
            val keyword = match.groupValues[1].uppercase()
            val kwGroup = match.groups[1]!!
            result += TextRange(start + kwGroup.range.first, start + kwGroup.range.last + 1) to
                TextAttributes().apply {
                    foregroundColor = Config.keywordColor(keyword)
                    fontType = if (Config.BOLD_KEYWORDS) Font.BOLD else Font.PLAIN
                }

            // Tag, including its brackets (e.g. "[auth]")
            val tag = match.groupValues[2].ifEmpty { null }
            if (tag != null) {
                val g = match.groups[2]!!
                val tagColor = Config.tagColor(tag)
                result += TextRange(start + g.range.first - 1, start + g.range.last + 2) to
                    TextAttributes().apply {
                        foregroundColor = tagColor
                        fontType = Font.BOLD or Font.ITALIC
                        if (Config.UNDERLINE_TAGS) {
                            effectType = EffectType.LINE_UNDERSCORE
                            effectColor = tagColor
                        }
                    }
            }

            // Priority, including its parentheses (e.g. "(high)")
            val priority = match.groupValues[3].ifEmpty { null }?.lowercase()
            if (priority != null) {
                val g = match.groups[3]!!
                result += TextRange(start + g.range.first - 1, start + g.range.last + 2) to
                    TextAttributes().apply {
                        foregroundColor = Config.priorityColor(priority)
                        fontType = if (priority == Config.CRITICAL_PRIORITY) Font.BOLD else Font.PLAIN
                    }
            }

            // Description, clipped so it stops before any closing delimiter / trailing whitespace.
            val descGroup = match.groups[4]
            if (descGroup != null && descGroup.value.isNotBlank()) {
                val descStart = start + descGroup.range.first
                var descEnd = (start + descGroup.range.last + 1).coerceAtMost(descLimit)
                while (descEnd > descStart && text[descEnd - start - 1].isWhitespace()) descEnd--
                if (descEnd > descStart) {
                    result += TextRange(descStart, descEnd) to
                        TextAttributes().apply { foregroundColor = Config.descriptionColor() }
                }
            }
        }
        return result
    }
}
