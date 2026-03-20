package org.kson.jetbrains.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.kson.jetbrains.parser.elem
import org.kson.parser.ParsedElementType
import org.kson.parser.TokenType

// --- Value types ---

class KsonPsiObject(node: ASTNode) : KsonPsiElement(node)

class KsonPsiArray(node: ASTNode) : KsonPsiElement(node)

class KsonPsiString(node: ASTNode) : KsonPsiElement(node) {
    /**
     * The text content of this string, with quotes stripped for quoted strings.
     *
     * For quoted strings (`QUOTED_STRING`), returns the `STRING_CONTENT` child text,
     * or empty string if the quoted string has no content (e.g. `""`).
     * For unquoted strings, returns the raw token text.
     */
    val textValue: String?
        get() {
            val stringContent = node.findChildByType(elem(TokenType.STRING_CONTENT))
            if (stringContent != null) return stringContent.text
            // For unquoted strings, the composite wraps a leaf token with the same type
            val unquotedToken = node.findChildByType(elem(TokenType.UNQUOTED_STRING))
            if (unquotedToken != null) return unquotedToken.text
            // Quoted string with no STRING_CONTENT child means empty string (e.g. "")
            if (node.elementType == elem(ParsedElementType.QUOTED_STRING)) return ""
            return null
        }
}

class KsonPsiNumber(node: ASTNode) : KsonPsiElement(node)

class KsonPsiBoolean(node: ASTNode) : KsonPsiElement(node)

class KsonPsiNull(node: ASTNode) : KsonPsiElement(node)

// --- Structural types ---

class KsonPsiProperty(node: ASTNode) : KsonPsiElement(node) {
    val key: KsonPsiObjectKey?
        get() = PsiTreeUtil.getChildOfType(this, KsonPsiObjectKey::class.java)

    val value: PsiElement?
        get() = children.firstOrNull { it.isKsonValueElement() }
}

class KsonPsiObjectKey(node: ASTNode) : KsonPsiElement(node) {
    /** The key name text, delegating to the child [KsonPsiString.textValue]. */
    val keyName: String?
        get() = PsiTreeUtil.getChildOfType(this, KsonPsiString::class.java)?.textValue
}

class KsonPsiListElement(node: ASTNode) : KsonPsiElement(node) {
    val value: PsiElement?
        get() = children.firstOrNull { it.isKsonValueElement() }
}

/**
 * Returns true if this [PsiElement] represents a KSON value (object, array, string, number, boolean, null, or embed block).
 */
private fun PsiElement.isKsonValueElement(): Boolean =
    this is KsonPsiObject ||
        this is KsonPsiArray ||
        this is KsonPsiString ||
        this is KsonPsiNumber ||
        this is KsonPsiBoolean ||
        this is KsonPsiNull ||
        this is KsonEmbedBlock
