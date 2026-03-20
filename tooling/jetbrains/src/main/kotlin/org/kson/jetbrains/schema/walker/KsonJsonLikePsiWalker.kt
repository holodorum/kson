package org.kson.jetbrains.schema.walker

import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.kson.jetbrains.psi.*

/**
 * Bridges KSON's typed PSI tree into IntelliJ's JSON Schema infrastructure.
 *
 * This walker allows IntelliJ to treat KSON files as schema-capable documents:
 * navigating properties, building JSON Pointer positions, and creating value adapters
 * that the schema validator can inspect.
 *
 * Modeled after [com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker] and
 * YAML's `YamlJsonPsiWalker`, adapted to KSON's PSI element types.
 */
object KsonJsonLikePsiWalker : JsonLikePsiWalker {

    override fun isName(element: PsiElement): ThreeState {
        val parent = element.parent
        // Inside an object key — this is a property name position
        if (parent is KsonPsiObjectKey || parent is KsonPsiString && parent.parent is KsonPsiObjectKey) {
            return ThreeState.YES
        }
        // Directly inside an object (e.g. typing a new key)
        if (parent is KsonPsiObject) {
            return ThreeState.YES
        }
        // Inside a list element — could be name or value
        if (parent is KsonPsiListElement) {
            return ThreeState.UNSURE
        }
        return ThreeState.NO
    }

    override fun isPropertyWithValue(element: PsiElement): Boolean {
        return element is KsonPsiProperty && element.value != null
    }

    override fun findElementToCheck(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is KsonPsiObject || current is KsonPsiArray ||
                current is KsonPsiString || current is KsonPsiNumber ||
                current is KsonPsiBoolean || current is KsonPsiNull ||
                current is KsonPsiProperty
            ) {
                return current
            }
            current = current.parent
        }
        return null
    }

    override fun findPosition(element: PsiElement, forceLastTransition: Boolean): JsonPointerPosition? {
        val pos = JsonPointerPosition()
        var current: PsiElement = element
        while (current !is PsiFile) {
            val position = current
            current = current.parent ?: return null

            when {
                current is KsonPsiArray && position is KsonPsiListElement -> {
                    val elements = PsiTreeUtil.getChildrenOfType(current, KsonPsiListElement::class.java)
                    val idx = elements?.indexOf(position) ?: -1
                    if (idx != -1) pos.addPrecedingStep(idx)
                }

                current is KsonPsiListElement -> {
                    // handled by the array case above when we go one level higher
                }

                current is KsonPsiProperty -> {
                    val propertyName = (current as KsonPsiProperty).key?.keyName ?: return null
                    current = current.parent
                    if (current !is KsonPsiObject) return null
                    if (position != element || forceLastTransition) {
                        pos.addPrecedingStep(propertyName)
                    }
                }

                current is KsonPsiObject && position is KsonPsiProperty -> {
                    val propertyName = position.key?.keyName ?: return null
                    if (position != element || forceLastTransition) {
                        pos.addPrecedingStep(propertyName)
                    }
                }

                current is KsonPsiObjectKey -> {
                    // inside a key — continue up to the property
                }

                current is PsiFile -> break

                else -> {
                    // skip structural wrappers (KsonPsiString inside KsonPsiObjectKey, etc.)
                    // and continue walking up
                }
            }
        }
        return pos
    }

    override fun requiresNameQuotes(): Boolean = false

    override fun requiresValueQuotes(): Boolean = false

    override fun allowsSingleQuotes(): Boolean = false

    override fun hasMissingCommaAfter(element: PsiElement): Boolean = false

    override fun getPropertyNamesOfParentObject(
        originalPosition: PsiElement,
        computedPosition: PsiElement?
    ): Set<String> {
        val obj = PsiTreeUtil.getParentOfType(originalPosition, KsonPsiObject::class.java)
            ?: return emptySet()
        return KsonObjectAdapter(obj).propertyList
            .mapNotNull { it.name }
            .toSet()
    }

    override fun getParentPropertyAdapter(element: PsiElement): JsonPropertyAdapter? {
        val property = PsiTreeUtil.getParentOfType(element, KsonPsiProperty::class.java, false)
            ?: return null
        // It's a parent property only if its value contains the element
        val value = property.value
        if (value == null || !PsiTreeUtil.isAncestor(value, element, true)) return null
        return KsonPropertyAdapter(property)
    }

    override fun isTopJsonElement(element: PsiElement): Boolean {
        return element is KsonPsiFile
    }

    override fun createValueAdapter(element: PsiElement): JsonValueAdapter? {
        return when (element) {
            is KsonPsiObject -> KsonObjectAdapter(element)
            is KsonPsiArray -> KsonArrayAdapter(element)
            is KsonPsiString, is KsonPsiNumber,
            is KsonPsiBoolean, is KsonPsiNull -> KsonGenericValueAdapter(element)
            else -> null
        }
    }

    override fun getRoots(file: PsiFile): Collection<PsiElement>? {
        if (file !is KsonPsiFile) return emptyList()
        val firstChild = file.firstChild ?: return emptyList()
        return listOf(firstChild)
    }

    override fun hasWhitespaceDelimitedCodeBlocks(): Boolean = true

    override fun acceptsEmptyRoot(): Boolean = true

    override fun getPropertyNameElement(property: PsiElement?): PsiElement? {
        if (property !is KsonPsiProperty) return null
        val key = property.key ?: return null
        return PsiTreeUtil.getChildOfType(key, KsonPsiString::class.java) ?: key
    }

    override fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter? {
        // Returning null disables schema-driven quick-fixes (add property, etc.)
        // which require generating KSON syntax. We can implement this later.
        return null
    }

    override fun getParentContainer(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(
            PsiTreeUtil.getParentOfType(element, KsonPsiProperty::class.java),
            KsonPsiObject::class.java, KsonPsiArray::class.java
        )
    }
}
