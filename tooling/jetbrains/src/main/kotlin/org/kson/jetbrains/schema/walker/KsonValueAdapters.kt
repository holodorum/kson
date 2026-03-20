package org.kson.jetbrains.schema.walker

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import org.kson.jetbrains.psi.*

/**
 * Adapts a [KsonPsiObject] to IntelliJ's [JsonObjectValueAdapter] interface,
 * allowing IntelliJ's JSON Schema infrastructure to inspect KSON objects.
 */
class KsonObjectAdapter(private val obj: KsonPsiObject) : JsonObjectValueAdapter {

    override fun isObject(): Boolean = true
    override fun isArray(): Boolean = false
    override fun isStringLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false
    override fun isBooleanLiteral(): Boolean = false
    override fun getDelegate(): PsiElement = obj
    override fun getAsObject(): JsonObjectValueAdapter = this
    override fun getAsArray(): JsonArrayValueAdapter? = null

    override fun getPropertyList(): List<JsonPropertyAdapter> {
        return PsiTreeUtil.getChildrenOfType(obj, KsonPsiProperty::class.java)
            ?.map { KsonPropertyAdapter(it) }
            ?: emptyList()
    }
}

/**
 * Adapts a [KsonPsiArray] to IntelliJ's [JsonArrayValueAdapter] interface.
 */
class KsonArrayAdapter(private val array: KsonPsiArray) : JsonArrayValueAdapter {

    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = true
    override fun isStringLiteral(): Boolean = false
    override fun isNumberLiteral(): Boolean = false
    override fun isBooleanLiteral(): Boolean = false
    override fun getDelegate(): PsiElement = array
    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getAsArray(): JsonArrayValueAdapter = this

    override fun getElements(): List<JsonValueAdapter> {
        return PsiTreeUtil.getChildrenOfType(array, KsonPsiListElement::class.java)
            ?.mapNotNull { elem -> elem.value?.let { createValueAdapter(it) } }
            ?: emptyList()
    }
}

/**
 * Adapts a [KsonPsiProperty] to IntelliJ's [JsonPropertyAdapter] interface.
 */
class KsonPropertyAdapter(private val property: KsonPsiProperty) : JsonPropertyAdapter {

    override fun getName(): String? = property.key?.keyName

    override fun getNameValueAdapter(): JsonValueAdapter? {
        val keyString = PsiTreeUtil.getChildOfType(property.key, KsonPsiString::class.java)
        return keyString?.let { KsonGenericValueAdapter(it) }
    }

    override fun getValues(): Collection<JsonValueAdapter> {
        val value = property.value ?: return emptyList()
        return listOf(createValueAdapter(value))
    }

    override fun getDelegate(): PsiElement = property

    override fun getParentObject(): JsonObjectValueAdapter? {
        val parent = property.parent as? KsonPsiObject ?: return null
        return KsonObjectAdapter(parent)
    }
}

/**
 * Adapts scalar KSON PSI elements (strings, numbers, booleans, nulls)
 * to IntelliJ's [JsonValueAdapter] interface.
 */
class KsonGenericValueAdapter(private val element: PsiElement) : JsonValueAdapter {

    override fun isObject(): Boolean = false
    override fun isArray(): Boolean = false
    override fun isStringLiteral(): Boolean = element is KsonPsiString
    override fun isNumberLiteral(): Boolean = element is KsonPsiNumber
    override fun isBooleanLiteral(): Boolean = element is KsonPsiBoolean
    override fun isNull(): Boolean = element is KsonPsiNull
    override fun getDelegate(): PsiElement = element
    override fun getAsObject(): JsonObjectValueAdapter? = null
    override fun getAsArray(): JsonArrayValueAdapter? = null
}

/**
 * Creates the appropriate [JsonValueAdapter] for a KSON PSI element.
 */
fun createValueAdapter(element: PsiElement): JsonValueAdapter = when (element) {
    is KsonPsiObject -> KsonObjectAdapter(element)
    is KsonPsiArray -> KsonArrayAdapter(element)
    else -> KsonGenericValueAdapter(element)
}
