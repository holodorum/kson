package org.kson.jetbrains.psi

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.jetbrains.file.KsonFileType

/**
 * Tests that [PsiElementWalker] correctly navigates the typed PSI tree.
 *
 * Parallels [org.kson.walker.AstNodeWalkerTest] and [org.kson.walker.KsonValueWalkerTest]
 * for the AST and KsonValue walkers respectively, adapted to operate on IntelliJ PSI elements.
 */
class PsiElementWalkerTest : BasePlatformTestCase() {

    private val walker = PsiElementWalker

    /** Parse KSON source into a PSI tree and return the root value element. */
    private fun parse(source: String): PsiElement {
        val psiFile = myFixture.configureByText(KsonFileType, source)
        return psiFile.firstChild ?: error("Expected a root PSI element")
    }

    // --- Type checks ---

    fun testIsObject() {
        val node = parse("name: Alice")
        assertTrue(walker.isObject(node))
        assertFalse(walker.isArray(node))
    }

    fun testIsArray() {
        val node = parse("- one\n- two")
        assertTrue(walker.isArray(node))
        assertFalse(walker.isObject(node))
    }

    fun testIsBracketArray() {
        val node = parse("[1, 2, 3]")
        assertTrue(walker.isArray(node))
        assertFalse(walker.isObject(node))
    }

    fun testIsDelimitedDashArray() {
        val node = parse("<\n- one\n- two\n>")
        assertTrue(walker.isArray(node))
    }

    fun testIsString() {
        val obj = parse("key: hello")
        val props = walker.getObjectProperties(obj)
        assertInstanceOf(props[0].value, KsonPsiString::class.java)
    }

    fun testIsNumber() {
        val obj = parse("key: 42")
        val props = walker.getObjectProperties(obj)
        assertInstanceOf(props[0].value, KsonPsiNumber::class.java)
    }

    fun testIsBoolean() {
        val obj = parse("key: true")
        val props = walker.getObjectProperties(obj)
        assertInstanceOf(props[0].value, KsonPsiBoolean::class.java)
    }

    fun testIsNull() {
        val obj = parse("key: null")
        val props = walker.getObjectProperties(obj)
        assertInstanceOf(props[0].value, KsonPsiNull::class.java)
    }

    // --- Property and element enumeration ---

    fun testGetObjectProperties() {
        val node = parse("name: Alice\nage: 30")
        val props = walker.getObjectProperties(node)
        assertEquals(2, props.size)
        assertEquals("name", props[0].name)
        assertEquals("age", props[1].name)
        assertInstanceOf(props[0].value, KsonPsiString::class.java)
        assertInstanceOf(props[1].value, KsonPsiNumber::class.java)
    }

    fun testGetObjectPropertiesOnNonObject() {
        val node = parse("- one\n- two")
        assertEquals(emptyList<Any>(), walker.getObjectProperties(node))
    }

    fun testGetArrayElements() {
        val node = parse("- one\n- two\n- three")
        val elements = walker.getArrayElements(node)
        assertEquals(3, elements.size)
        assertTrue(elements.all { it is KsonPsiString })
    }

    fun testGetBracketArrayElements() {
        val node = parse("[1, 2, 3]")
        val elements = walker.getArrayElements(node)
        assertEquals(3, elements.size)
        assertTrue(elements.all { it is KsonPsiNumber })
    }

    fun testGetArrayElementsOnNonArray() {
        val node = parse("key: value")
        assertEquals(emptyList<Any>(), walker.getArrayElements(node))
    }

    // --- String values ---

    fun testGetStringValueUnquoted() {
        val obj = parse("key: hello")
        val value = walker.getObjectProperties(obj)[0].value
        assertEquals("hello", walker.getStringValue(value))
    }

    fun testGetStringValueQuoted() {
        val obj = parse("""key: "hello world"""")
        val value = walker.getObjectProperties(obj)[0].value
        assertEquals("hello world", walker.getStringValue(value))
    }

    fun testGetStringValueEmpty() {
        val obj = parse("""key: "" """.trim())
        val value = walker.getObjectProperties(obj)[0].value
        assertEquals("", walker.getStringValue(value))
    }

    fun testGetStringValueOnNonString() {
        val obj = parse("key: 42")
        val value = walker.getObjectProperties(obj)[0].value
        assertNull(walker.getStringValue(value))
    }

    // --- Quoted property keys ---

    fun testQuotedPropertyKey() {
        val obj = parse(""""my key": value""")
        val props = walker.getObjectProperties(obj)
        assertEquals(1, props.size)
        assertEquals("my key", props[0].name)
    }

    // --- Location ---

    fun testGetLocation() {
        val node = parse("name: Alice")
        val location = walker.getLocation(node)
        assertEquals(0, location.start.line)
        assertEquals(0, location.start.column)
        assertEquals(0, location.startOffset)
    }

    // --- Nested structures ---

    fun testNestedObject() {
        val obj = parse("{outer: {inner: value}}")
        val outerProps = walker.getObjectProperties(obj)
        assertEquals(1, outerProps.size)
        assertEquals("outer", outerProps[0].name)
        assertTrue(walker.isObject(outerProps[0].value))
        val innerProps = walker.getObjectProperties(outerProps[0].value)
        assertEquals(1, innerProps.size)
        assertEquals("inner", innerProps[0].name)
        assertEquals("value", walker.getStringValue(innerProps[0].value))
    }

    fun testArrayOfObjects() {
        val arr = parse("- name: Alice\n- name: Bob")
        val elements = walker.getArrayElements(arr)
        assertEquals(2, elements.size)
        assertTrue(elements.all { walker.isObject(it) })
        assertEquals("Alice", walker.getStringValue(walker.getObjectProperties(elements[0])[0].value))
        assertEquals("Bob", walker.getStringValue(walker.getObjectProperties(elements[1])[0].value))
    }

    // --- Embed block as leaf ---

    fun testEmbedBlockIsLeaf() {
        val obj = parse("code:\n  %sql\n  SELECT 1\n  %%")
        val props = walker.getObjectProperties(obj)
        assertEquals(1, props.size)
        val embedValue = props[0].value
        assertInstanceOf(embedValue, KsonEmbedBlock::class.java)
        assertFalse(walker.isObject(embedValue))
        assertFalse(walker.isArray(embedValue))
        assertNull(walker.getStringValue(embedValue))
    }
}
