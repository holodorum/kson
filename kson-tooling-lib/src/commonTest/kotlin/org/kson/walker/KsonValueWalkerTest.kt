package org.kson.walker

import org.kson.KsonCore
import org.kson.value.*
import kotlin.test.*

/**
 * Tests that [KsonValueWalker] correctly delegates to the [KsonValue] sealed hierarchy.
 */
class KsonValueWalkerTest {

    private val walker = KsonValueWalker

    private fun parse(source: String): KsonValue =
        KsonCore.parseToAst(source).ksonValue
            ?: error("Parse failed")

    @Test
    fun testIsObject() {
        val value = parse("name: Alice")
        assertTrue(walker.isObject(value))
        assertFalse(walker.isArray(value))
    }

    @Test
    fun testIsArray() {
        val value = parse("- one\n- two")
        assertTrue(walker.isArray(value))
        assertFalse(walker.isObject(value))
    }

    @Test
    fun testIsString() {
        val obj = parse("key: hello") as KsonObject
        val stringValue = obj.propertyLookup["key"]!!
        assertIs<KsonString>(stringValue)
    }

    @Test
    fun testIsNumber() {
        val obj = parse("key: 42") as KsonObject
        val numValue = obj.propertyLookup["key"]!!
        assertIs<KsonNumber>(numValue)
    }

    @Test
    fun testIsBoolean() {
        val obj = parse("key: true") as KsonObject
        val boolValue = obj.propertyLookup["key"]!!
        assertIs<KsonBoolean>(boolValue)
    }

    @Test
    fun testIsNull() {
        val obj = parse("key: null") as KsonObject
        val nullValue = obj.propertyLookup["key"]!!
        assertIs<KsonNull>(nullValue)
    }

    @Test
    fun testGetObjectProperties() {
        val value = parse("name: Alice\nage: 30")
        val props = walker.getObjectProperties(value)
        assertEquals(2, props.size)
        assertEquals("name", props[0].first)
        assertEquals("age", props[1].first)
        assertIs<KsonString>(props[0].second)
        assertIs<KsonNumber>(props[1].second)
    }

    @Test
    fun testGetObjectPropertiesOnNonObject() {
        val value = parse("- one\n- two")
        assertEquals(emptyList(), walker.getObjectProperties(value))
    }

    @Test
    fun testGetArrayElements() {
        val value = parse("- one\n- two\n- three")
        val elements = walker.getArrayElements(value)
        assertEquals(3, elements.size)
        assertTrue(elements.all { it is KsonString })
    }

    @Test
    fun testGetArrayElementsOnNonArray() {
        val value = parse("key: value")
        assertEquals(emptyList(), walker.getArrayElements(value))
    }

    @Test
    fun testGetStringValue() {
        val obj = parse("key: hello") as KsonObject
        val stringValue = obj.propertyLookup["key"]!!
        assertEquals("hello", walker.getStringValue(stringValue))
    }

    @Test
    fun testGetStringValueOnNonString() {
        val obj = parse("key: 42") as KsonObject
        val numValue = obj.propertyLookup["key"]!!
        assertNull(walker.getStringValue(numValue))
    }

    @Test
    fun testEmbedBlockIsTreatedAsLeaf() {
        val obj = parse("content: %\n  hello\n%%") as KsonObject
        val embedValue = obj.propertyLookup["content"]!!
        // EmbedBlock is not an object or array — it's a leaf from the walker's perspective
        assertFalse(walker.isObject(embedValue))
        assertFalse(walker.isArray(embedValue))
        assertIs<EmbedBlock>(embedValue)
        // No children to enumerate
        assertEquals(emptyList(), walker.getObjectProperties(embedValue))
        assertEquals(emptyList(), walker.getArrayElements(embedValue))
    }

    @Test
    fun testGetLocation() {
        val value = parse("name: Alice")
        val location = walker.getLocation(value)
        assertEquals(0, location.start.line)
        assertEquals(0, location.start.column)
    }
}
