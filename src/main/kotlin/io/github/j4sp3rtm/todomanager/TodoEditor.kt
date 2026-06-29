package io.github.j4sp3rtm.todomanager

import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

/**
 * Handles writing TODO edits from the panel back into source comments.
 * All modifications use WriteCommandAction for undo support.
 */
object TodoEditor {

    /**
     * Rebuilds the full comment text from parts and replaces it in the document.
     */
    fun setDescription(project: Project, item: TodoItem, newDescription: String) {
        rewriteComment(project, item, item.keyword, item.tag, item.priority, newDescription)
    }

    fun setTag(project: Project, item: TodoItem, newTag: String?) {
        rewriteComment(project, item, item.keyword, newTag, item.priority, item.description)
    }

    fun setPriority(project: Project, item: TodoItem, newPriority: String?) {
        rewriteComment(project, item, item.keyword, item.tag, newPriority, item.description)
    }

    fun markDone(project: Project, item: TodoItem) {
        val signed = appendSignature(item.description, GitUser.doneSignature(project))
        rewriteComment(project, item, Config.DONE_KEYWORD, item.tag, null, signed)
    }

    /** Appends a completion stamp to a description, e.g. "fix login (done by Jasper on 2026-06-29)". */
    private fun appendSignature(description: String, signature: String): String {
        val base = description.trimEnd()
        return if (base.isEmpty()) "($signature)" else "$base ($signature)"
    }

    fun delete(project: Project, item: TodoItem) {
        val document = getDocument(item) ?: return
        val matchRange = item.matchRange ?: return
        val textRange = item.textRange ?: return
        WriteCommandAction.runWriteCommandAction(project, "Delete TODO", null, {
            val matchStart = matchRange.startOffset
            val lineNum = document.getLineNumber(matchStart)
            val lineStart = document.getLineStartOffset(lineNum)
            val lineEnd = document.getLineEndOffset(lineNum)

            if (!item.isBlockComment) {
                // Line comment: delete the whole line if the comment is the only content
                val commentText = document.getText(com.intellij.openapi.util.TextRange(
                    textRange.startOffset, textRange.endOffset
                )).trim()
                val lineText = document.getText(
                    com.intellij.openapi.util.TextRange(lineStart, lineEnd)
                ).trim()

                if (lineText == commentText) {
                    val deleteEnd = if (lineEnd < document.textLength) lineEnd + 1 else lineEnd
                    document.deleteString(lineStart, deleteEnd)
                } else {
                    document.deleteString(textRange.startOffset, textRange.endOffset)
                }
            } else {
                // Block/doc comment: delete the line containing this TODO match
                val deleteEnd = if (lineEnd < document.textLength) lineEnd + 1 else lineEnd
                document.deleteString(lineStart, deleteEnd)
            }
        })
    }

    /**
     * Inserts a new TODO comment at the current cursor position.
     */
    fun insertNew(project: Project, keyword: String, tag: String?, priority: String?, description: String) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val lineNum = document.getLineNumber(caretOffset)
        val lineEnd = document.getLineEndOffset(lineNum)

        val commentText = buildCommentText(keyword, tag, priority, description)
        val indent = getLineIndent(document, lineNum)
        val insertion = "\n${indent}${wrapInComment(project, document, commentText)}"

        WriteCommandAction.runWriteCommandAction(project, "Insert TODO", null, {
            document.insertString(lineEnd, insertion)
        })
    }

    /**
     * Wraps the comment text in the appropriate comment syntax for the document's language,
     * preferring line comments and falling back to block comments, then "//".
     */
    private fun wrapInComment(project: Project, document: Document, commentText: String): String {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        val commenter = psiFile?.language?.let { LanguageCommenters.INSTANCE.forLanguage(it) }

        commenter?.lineCommentPrefix?.let { prefix ->
            val sep = if (prefix.last().isLetterOrDigit() || prefix.endsWith(" ")) "" else " "
            return "$prefix$sep$commentText"
        }
        val open = commenter?.blockCommentPrefix
        val close = commenter?.blockCommentSuffix
        if (open != null && close != null) {
            return "$open $commentText $close"
        }
        return "// $commentText"
    }

    /* ============ Internal ============ */

    private fun rewriteComment(
        project: Project,
        item: TodoItem,
        keyword: String,
        tag: String?,
        priority: String?,
        description: String
    ) {
        val document = getDocument(item) ?: return
        val matchRange = item.matchRange ?: return
        val newText = buildCommentText(keyword, tag, priority, description)

        WriteCommandAction.runWriteCommandAction(project, "Edit TODO", null, {
            document.replaceString(matchRange.startOffset, matchRange.endOffset, newText)
        })
    }

    /**
     * Builds the inner comment text: KEYWORD [tag] (priority) description
     */
    private fun buildCommentText(keyword: String, tag: String?, priority: String?, description: String): String {
        val parts = mutableListOf(keyword)
        if (tag != null) parts.add("[$tag]")
        if (priority != null) parts.add("($priority)")
        parts.add(description)
        return parts.joinToString(" ")
    }

    private fun getDocument(item: TodoItem): Document? {
        val file = item.file ?: return null
        return FileDocumentManager.getInstance().getDocument(file)
    }

    private fun getLineIndent(document: Document, lineNum: Int): String {
        val lineStart = document.getLineStartOffset(lineNum)
        val lineEnd = document.getLineEndOffset(lineNum)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
        return lineText.takeWhile { it == ' ' || it == '\t' }
    }
}
