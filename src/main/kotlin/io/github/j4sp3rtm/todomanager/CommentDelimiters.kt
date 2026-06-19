package io.github.j4sp3rtm.todomanager

import com.intellij.lang.LanguageCommenters
import com.intellij.psi.PsiComment

/**
 * Resolves the opening/closing delimiters of a [PsiComment] using the language's
 * registered Commenter, so delimiter handling works across every language
 * (`//`, `#`, `--`, `/* */`, `<!-- -->`, …) without hardcoding syntax.
 */
internal object CommentDelimiters {

    /**
     * The comment's (prefix, suffix). [suffix] is null for line comments. Either component
     * is null when the delimiter cannot be determined for the element's language.
     */
    fun of(comment: PsiComment): Pair<String?, String?> {
        val commenter = LanguageCommenters.INSTANCE.forLanguage(comment.language)
            ?: return null to null
        val text = comment.text

        val blockPrefix = commenter.blockCommentPrefix
        val blockSuffix = commenter.blockCommentSuffix
        if (!blockPrefix.isNullOrEmpty() && !blockSuffix.isNullOrEmpty() &&
            text.startsWith(blockPrefix) && text.endsWith(blockSuffix) &&
            text.length >= blockPrefix.length + blockSuffix.length
        ) {
            return blockPrefix to blockSuffix
        }

        val linePrefix = commenter.lineCommentPrefix
        if (!linePrefix.isNullOrEmpty() && text.startsWith(linePrefix)) {
            return linePrefix to null
        }

        return null to null
    }
}
