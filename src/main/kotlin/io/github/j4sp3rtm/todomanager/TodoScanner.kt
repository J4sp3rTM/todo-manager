package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor

/**
 * Scans a [PsiFile] for TODO-style comments matching the configured keywords.
 *
 * Language-agnostic: it walks the PSI tree for any [PsiComment] node, so it works for
 * every language whose parser produces comment elements (Java, Kotlin, Python, JS,
 * Go, XML/HTML, shell, properties, …). Multiple TODOs in one block comment are each
 * extracted as separate items.
 */
object TodoScanner {

    fun scan(file: PsiFile, document: Document?, virtualFile: VirtualFile?): List<TodoItem> {
        if (virtualFile == null) return emptyList()
        val pattern = TodoPattern.build(Config.KEYWORDS)
        val items = mutableListOf<TodoItem>()

        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiComment) {
                    scanComment(element, pattern, document, virtualFile, items)
                }
                super.visitElement(element)
            }
        })

        return items
    }

    private fun scanComment(
        comment: PsiComment,
        pattern: Regex,
        document: Document?,
        virtualFile: VirtualFile,
        items: MutableList<TodoItem>
    ) {
        val text = comment.text
        val elementStart = comment.textRange.startOffset
        // Treat any multi-line comment as a block comment for edit/delete purposes.
        val isBlock = text.contains('\n')
        // Closing delimiter (e.g. "*/", "-->") of a single-line block comment, if any.
        val (_, suffix) = CommentDelimiters.of(comment)

        for (match in pattern.findAll(text)) {
            val keyword = match.groupValues[1].uppercase()
            val tag = match.groupValues[2].ifEmpty { null }
            val priority = match.groupValues[3].ifEmpty { null }?.lowercase()

            var description = match.groupValues[4]
            val matchStartAbs = elementStart + match.range.first
            var matchEndAbs = elementStart + match.range.last + 1

            // When the keyword is on the comment's closing line, the description regex greedily
            // captures the closing delimiter (e.g. "fix this */"). Strip it from both the
            // description and the editable match range so write-back never clobbers the delimiter.
            if (suffix != null && match.range.last + 1 == text.length && description.endsWith(suffix)) {
                val trimmed = description.removeSuffix(suffix).trimEnd()
                matchEndAbs -= description.length - trimmed.length
                description = trimmed
            }
            description = description.trim()

            val matchRange = TextRange(matchStartAbs, matchEndAbs)
            val lineNumber = document?.getLineNumber(matchStartAbs) ?: 0

            items.add(
                TodoItem(
                    keyword = keyword,
                    tag = tag,
                    priority = priority,
                    description = description,
                    file = virtualFile,
                    line = lineNumber,
                    textRange = comment.textRange,
                    matchRange = matchRange,
                    originalText = text.substring(match.range.first, matchEndAbs - elementStart),
                    isBlockComment = isBlock,
                )
            )
        }
    }
}
