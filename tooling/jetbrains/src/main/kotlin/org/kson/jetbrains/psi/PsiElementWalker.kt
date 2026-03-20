package org.kson.jetbrains.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.walker.KsonTreeWalker
import org.kson.walker.TreeProperty

/**
 * [KsonTreeWalker] implementation for IntelliJ PSI elements.
 *
 * This walker navigates the typed PSI tree produced by [org.kson.jetbrains.parser.KsonParserDefinition],
 * enabling tree-walking algorithms (JSON Pointer traversal, location-based lookup, path building)
 * to operate directly on IntelliJ's PSI representation without converting to AST or KsonValue.
 */
object PsiElementWalker : KsonTreeWalker<PsiElement> {

    override fun isObject(node: PsiElement): Boolean = node is KsonPsiObject

    override fun isArray(node: PsiElement): Boolean = node is KsonPsiArray

    override fun getObjectProperties(node: PsiElement): List<TreeProperty<PsiElement>> {
        if (node !is KsonPsiObject) return emptyList()
        return PsiTreeUtil.getChildrenOfType(node, KsonPsiProperty::class.java)
            ?.mapNotNull { prop ->
                val keyName = prop.key?.keyName ?: return@mapNotNull null
                val value = prop.value ?: return@mapNotNull null
                TreeProperty(keyName, value)
            }
            ?: emptyList()
    }

    override fun getArrayElements(node: PsiElement): List<PsiElement> {
        if (node !is KsonPsiArray) return emptyList()
        return PsiTreeUtil.getChildrenOfType(node, KsonPsiListElement::class.java)
            ?.mapNotNull { it.value }
            ?: emptyList()
    }

    override fun getStringValue(node: PsiElement): String? {
        return (node as? KsonPsiString)?.textValue
    }

    override fun getLocation(node: PsiElement): Location {
        val startOffset = node.textRange.startOffset
        val endOffset = node.textRange.endOffset
        val document = node.containingFile?.viewProvider?.document
            ?: return Location(Coordinates(0, 0), Coordinates(0, 0), startOffset, endOffset)

        val startLine = document.getLineNumber(startOffset)
        val endLine = document.getLineNumber(endOffset)

        return Location(
            start = Coordinates(startLine, startOffset - document.getLineStartOffset(startLine)),
            end = Coordinates(endLine, endOffset - document.getLineStartOffset(endLine)),
            startOffset = startOffset,
            endOffset = endOffset
        )
    }
}
