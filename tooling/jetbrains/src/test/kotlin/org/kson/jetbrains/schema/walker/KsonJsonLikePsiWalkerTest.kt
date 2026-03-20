package org.kson.jetbrains.schema.walker

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThreeState
import com.jetbrains.jsonSchema.impl.JsonSchemaReader
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.*

/**
 * Tests for [KsonJsonLikePsiWalker] and the [KsonObjectAdapter], [KsonArrayAdapter],
 * [KsonPropertyAdapter], and [KsonGenericValueAdapter] classes it delegates to.
 *
 * These tests validate the bridge between KSON's typed PSI tree and IntelliJ's
 * JSON Schema infrastructure, ensuring .kson files can be used as schema sources.
 */
class KsonJsonLikePsiWalkerTest : BasePlatformTestCase() {

    private val walker = KsonJsonLikePsiWalker

    private fun parse(source: String): PsiElement {
        val psiFile = myFixture.configureByText(KsonFileType, source)
        return psiFile.firstChild ?: error("Expected a root PSI element")
    }

    // --- createValueAdapter ---

    fun testCreateValueAdapterForObject() {
        val root = parse("key: value")
        val adapter = walker.createValueAdapter(root)
        assertNotNull(adapter)
        assertTrue("Should be object adapter", adapter!!.isObject)
        assertNotNull("Should have getAsObject", adapter.asObject)
        assertNull("Should not have getAsArray", adapter.asArray)
    }

    fun testCreateValueAdapterForArray() {
        val root = parse("- one\n- two")
        val adapter = walker.createValueAdapter(root)
        assertNotNull(adapter)
        assertTrue("Should be array adapter", adapter!!.isArray)
        assertNotNull("Should have getAsArray", adapter.asArray)
        assertNull("Should not have getAsObject", adapter.asObject)
    }

    fun testCreateValueAdapterForString() {
        val root = parse("key: hello")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val value = prop.value!!
        val adapter = walker.createValueAdapter(value)
        assertNotNull(adapter)
        assertTrue("Should be string literal", adapter!!.isStringLiteral)
        assertFalse(adapter.isObject)
        assertFalse(adapter.isArray)
    }

    fun testCreateValueAdapterForNumber() {
        val root = parse("key: 42")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val adapter = walker.createValueAdapter(prop.value!!)
        assertNotNull(adapter)
        assertTrue("Should be number literal", adapter!!.isNumberLiteral)
    }

    fun testCreateValueAdapterForBoolean() {
        val root = parse("key: true")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val adapter = walker.createValueAdapter(prop.value!!)
        assertNotNull(adapter)
        assertTrue("Should be boolean literal", adapter!!.isBooleanLiteral)
    }

    fun testCreateValueAdapterForNull() {
        val root = parse("key: null")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val adapter = walker.createValueAdapter(prop.value!!)
        assertNotNull(adapter)
        assertTrue("Should be null", adapter!!.isNull)
    }

    fun testCreateValueAdapterReturnsNullForUnknownElement() {
        val root = parse("key: value")
        // The property itself is not a value type
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        assertNull(walker.createValueAdapter(prop))
    }

    // --- Object adapter ---

    fun testObjectAdapterPropertyList() {
        val root = parse("name: Alice\nage: 30")
        val adapter = walker.createValueAdapter(root)!!.asObject!!
        val props = adapter.propertyList
        assertEquals(2, props.size)
        assertEquals("name", props[0].name)
        assertEquals("age", props[1].name)
    }

    fun testPropertyAdapterValues() {
        val root = parse("key: value")
        val adapter = walker.createValueAdapter(root)!!.asObject!!
        val prop = adapter.propertyList[0]
        val values = prop.values
        assertEquals(1, values.size)
        assertTrue(values.first().isStringLiteral)
    }

    fun testPropertyAdapterParentObject() {
        val root = parse("key: value")
        val adapter = walker.createValueAdapter(root)!!.asObject!!
        val prop = adapter.propertyList[0]
        assertNotNull(prop.parentObject)
        assertTrue(prop.parentObject!!.isObject)
    }

    // --- Array adapter ---

    fun testArrayAdapterElements() {
        val root = parse("[1, 2, 3]")
        val adapter = walker.createValueAdapter(root)!!.asArray!!
        val elements = adapter.elements
        assertEquals(3, elements.size)
        assertTrue(elements.all { it.isNumberLiteral })
    }

    fun testDashListAdapterElements() {
        val root = parse("- one\n- two\n- three")
        val adapter = walker.createValueAdapter(root)!!.asArray!!
        val elements = adapter.elements
        assertEquals(3, elements.size)
        assertTrue(elements.all { it.isStringLiteral })
    }

    // --- findPosition ---

    fun testFindPositionAtRoot() {
        val root = parse("key: value")
        val pos = walker.findPosition(root, false)
        assertNotNull(pos)
        assertEquals("/", pos!!.toJsonPointer())
    }

    fun testFindPositionAtPropertyValue() {
        val root = parse("key: value")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val value = prop.value!!
        val pos = walker.findPosition(value, true)
        assertNotNull(pos)
        assertEquals("/key", pos!!.toJsonPointer())
    }

    fun testFindPositionNested() {
        val root = parse("{outer: {inner: value}}")
        val innerObj = PsiTreeUtil.findChildrenOfType(root, KsonPsiObject::class.java)
            .first { it != root }
        val innerProp = PsiTreeUtil.findChildOfType(innerObj, KsonPsiProperty::class.java)!!
        val value = innerProp.value!!
        val pos = walker.findPosition(value, true)
        assertNotNull(pos)
        assertEquals("/outer/inner", pos!!.toJsonPointer())
    }

    fun testFindPositionInArray() {
        val root = parse("[10, 20, 30]")
        val elements = PsiTreeUtil.findChildrenOfType(root, KsonPsiListElement::class.java)
        val secondElement = elements.toList()[1]
        val value = secondElement.value!!
        val pos = walker.findPosition(value, true)
        assertNotNull(pos)
        assertEquals("/1", pos!!.toJsonPointer())
    }

    // --- isName ---

    fun testIsNameForObjectKey() {
        val root = parse("key: value")
        val key = PsiTreeUtil.findChildOfType(root, KsonPsiObjectKey::class.java)!!
        val keyString = PsiTreeUtil.findChildOfType(key, KsonPsiString::class.java)!!
        assertEquals(ThreeState.YES, walker.isName(keyString))
    }

    fun testIsNameForValue() {
        val root = parse("key: value")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val value = prop.value!!
        assertEquals(ThreeState.NO, walker.isName(value))
    }

    // --- isPropertyWithValue ---

    fun testIsPropertyWithValue() {
        val root = parse("key: value")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        assertTrue(walker.isPropertyWithValue(prop))
    }

    fun testIsPropertyWithValueFalseForNonProperty() {
        val root = parse("key: value")
        assertFalse(walker.isPropertyWithValue(root))
    }

    // --- findElementToCheck ---

    fun testFindElementToCheckFindsObject() {
        val root = parse("key: value")
        assertInstanceOf(walker.findElementToCheck(root), KsonPsiObject::class.java)
    }

    fun testFindElementToCheckFindsValue() {
        val root = parse("key: value")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val value = prop.value!!
        assertInstanceOf(walker.findElementToCheck(value), KsonPsiString::class.java)
    }

    // --- getRoots ---

    fun testGetRoots() {
        val psiFile = myFixture.configureByText(KsonFileType, "key: value") as KsonPsiFile
        val roots = walker.getRoots(psiFile)!!
        assertEquals(1, roots.size)
        assertInstanceOf(roots.first(), KsonPsiObject::class.java)
    }

    // --- getPropertyNamesOfParentObject ---

    fun testGetPropertyNamesOfParentObject() {
        val root = parse("name: Alice\nage: 30")
        val prop = PsiTreeUtil.findChildOfType(root, KsonPsiProperty::class.java)!!
        val value = prop.value!!
        val names = walker.getPropertyNamesOfParentObject(value, null)
        assertEquals(setOf("name", "age"), names)
    }

    // --- Integration: JsonSchemaReader can read a .kson schema file ---
    //
    // This is the flow that was failing before the walker was implemented:
    // User selects a .kson file in JSON Schema Mappings UI → IntelliJ calls
    // JsonSchemaReader.checkIfValidJsonSchema → readFromFile → read(psiFile) →
    // JsonLikePsiWalker.getWalker → our factory → our walker → our adapters.

    fun testJsonSchemaReaderAcceptsKsonSchemaFile() {
        val schemaFile = myFixture.addFileToProject("my.schema.kson", """
            type: object
            properties:
              name:
                type: string
                .
              .
        """.trimIndent())

        val error = JsonSchemaReader.checkIfValidJsonSchema(project, schemaFile.virtualFile)
        assertNull("JsonSchemaReader should accept a .kson schema file without error, got: $error", error)
    }

    fun testJsonSchemaReaderAcceptsSimpleKsonSchema() {
        val schemaFile = myFixture.addFileToProject("simple.schema.kson", "type: string")

        val error = JsonSchemaReader.checkIfValidJsonSchema(project, schemaFile.virtualFile)
        assertNull("JsonSchemaReader should accept a simple .kson schema, got: $error", error)
    }

    fun testJsonSchemaReaderAcceptsKsonSchemaWithArrays() {
        val schemaFile = myFixture.addFileToProject("array.schema.kson", """
            type: object
            required: ["name", "age"]
        """.trimIndent())

        val error = JsonSchemaReader.checkIfValidJsonSchema(project, schemaFile.virtualFile)
        assertNull("JsonSchemaReader should accept a .kson schema with arrays, got: $error", error)
    }
}
