package org.kson.jetbrains.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.psi.*
import org.kson.parser.ParsedElementType
import org.kson.parser.TokenType

class KsonParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer {
        return KsonLexer()
    }

    override fun createParser(project: Project?): PsiParser {
        return KsonParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return IFileElementType(KsonLanguage)
    }

    override fun getCommentTokens(): TokenSet {
        return commentTokenSet
    }

    override fun getWhitespaceTokens(): TokenSet {
        return whitespaceTokenSet
    }

    override fun getStringLiteralElements(): TokenSet {
        return stringTokenSet
    }

    override fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            // Structural types
            elem(ParsedElementType.OBJECT) -> KsonPsiObject(node)
            elem(ParsedElementType.OBJECT_PROPERTY) -> KsonPsiProperty(node)
            elem(ParsedElementType.OBJECT_KEY) -> KsonPsiObjectKey(node)
            elem(ParsedElementType.DASH_LIST) -> KsonPsiArray(node)
            elem(ParsedElementType.DASH_DELIMITED_LIST) -> KsonPsiArray(node)
            elem(ParsedElementType.BRACKET_LIST) -> KsonPsiArray(node)
            elem(ParsedElementType.LIST_ELEMENT) -> KsonPsiListElement(node)

            // Value types
            elem(ParsedElementType.QUOTED_STRING) -> KsonPsiString(node)
            elem(TokenType.UNQUOTED_STRING) -> KsonPsiString(node)
            elem(TokenType.NUMBER) -> KsonPsiNumber(node)
            elem(TokenType.TRUE) -> KsonPsiBoolean(node)
            elem(TokenType.FALSE) -> KsonPsiBoolean(node)
            elem(TokenType.NULL) -> KsonPsiNull(node)

            // Embed types
            elem(ParsedElementType.EMBED_BLOCK) -> KsonEmbedBlock(node)
            elem(TokenType.EMBED_CONTENT) -> KsonEmbedContent(node)

            else -> KsonPsiElement(node)
        }
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return KsonPsiFile(viewProvider)
    }

}

private val commentTokenSet = TokenSet.create(elem(TokenType.COMMENT))
private val whitespaceTokenSet = TokenSet.create(elem(TokenType.WHITESPACE))
private val stringTokenSet =
    TokenSet.create(elem(ParsedElementType.OBJECT_KEY), elem(TokenType.STRING_CONTENT), elem(TokenType.UNQUOTED_STRING))
