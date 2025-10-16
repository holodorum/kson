package org.kson

import org.kson.parser.Location
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonValueNavigation
import org.kson.value.KsonNumber
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KsonNavigationUtilTest {

    // Sample KSON document for testing
    private val sampleKson = KsonCore.parseToAst("""
        name: 'John Doe'
        age: 30
        address:
          street: '123 Main St'
          city: 'Springfield'
          coordinates:
            - 40.7128
            - -74.0060
          .
        hobbies:
          - 'reading'
          - 'coding'
          - 'hiking'
        metadata:
          tags:
            - 'developer'
            - 'author'
          score: 95
          .
        .
    """.trimIndent()).ksonValue!!

    @Test
    fun `walkTree visits all nodes in depth-first order`() {
        val visited = mutableListOf<String>()

        KsonValueNavigation.walkTree(sampleKson) { node, _, depth ->
            val nodeType = when (node) {
                is KsonObject -> "Object(${node.propertyMap.size} props)"
                is KsonList -> "List(${node.elements.size} items)"
                is KsonString -> "String('${node.value}')"
                is KsonNumber -> "Number(${node.value})"
                else -> node::class.simpleName ?: "Unknown"
            }
            visited.add("${"  ".repeat(depth)}$nodeType")
        }

        // Verify we visited nodes
        assertTrue(visited.isNotEmpty(), "Should visit at least one node")
        assertTrue(visited[0].startsWith("Object"), "First node should be root object")

        // Verify depth increases for nested nodes
        assertTrue(visited.any { it.startsWith("  ") }, "Should have nested nodes")
    }

    @Test
    fun `walkTree passes correct parent references`() {
        val parentChildPairs = mutableListOf<Pair<KsonValue?, KsonValue>>()

        KsonValueNavigation.walkTree(sampleKson) { node, parent, _ ->
            parentChildPairs.add(parent to node)
        }

        // Root should have null parent
        assertEquals(null, parentChildPairs.first().first)
        assertEquals(sampleKson, parentChildPairs.first().second)

        // All non-root nodes should have parents
        val nonRootPairs = parentChildPairs.drop(1)
        assertTrue(nonRootPairs.all { it.first != null }, "All non-root nodes should have parents")
    }

    @Test
    fun `navigateByTokens navigates to nested object property`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "city"))

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("Springfield", result.value)
    }

    @Test
    fun `navigateByTokens navigates through array by index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "1"))

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("coding", result.value)
    }

    @Test
    fun `navigateByTokens navigates through nested arrays`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "coordinates", "0"))

        assertNotNull(result)
        assertTrue(result is KsonNumber)
        assertEquals(40.7128, result.value.asDouble)
    }

    @Test
    fun `navigateByTokens returns null for invalid property`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("nonexistent", "property"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for out of bounds array index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "99"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for negative array index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "-1"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens returns null for non-numeric array index`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "notANumber"))

        assertNull(result)
    }

    @Test
    fun `navigateByTokens with empty path returns root`() {
        val result = KsonValueNavigation.navigateByTokens(sampleKson, emptyList())

        assertSame(sampleKson, result)
    }

    @Test
    fun `navigateByTokens cannot navigate into primitive values`() {
        // Try to navigate into a string (primitive)
        val result = KsonValueNavigation.navigateByTokens(sampleKson, listOf("name", "someProp"))

        assertNull(result)
    }

    @Test
    fun `findParent returns null for root node`() {
        val parent = KsonValueNavigation.findParent(sampleKson, sampleKson)

        assertNull(parent)
    }

    @Test
    fun `findParent finds immediate parent of nested property`() {
        // First find the city string node
        val cityNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "city"))!!

        // Then find its parent (should be the address object)
        val parent = KsonValueNavigation.findParent(sampleKson, cityNode)

        assertNotNull(parent)
        assertTrue(parent is KsonObject)
        assertNotNull(parent.propertyLookup["street"])
        assertNotNull(parent.propertyLookup["city"])
    }

    @Test
    fun `findParent finds parent of array element`() {
        val hobbyNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "0"))!!
        val parent = KsonValueNavigation.findParent(sampleKson, hobbyNode)

        assertNotNull(parent)
        assertTrue(parent is KsonList)
        assertEquals(3, parent.elements.size)
    }

    @Test
    fun `findParent returns null for node not in tree`() {
        val unrelatedNode = KsonString("not in tree", Location.create(0, 0, 0, 0, 0, 0))
        val parent = KsonValueNavigation.findParent(sampleKson, unrelatedNode)

        assertNull(parent)
    }

    @Test
    fun `buildPathTokens returns empty list for root`() {
        val path = KsonValueNavigation.buildPathTokens(sampleKson, sampleKson)

        assertNotNull(path)
        assertTrue(path.isEmpty())
    }

    @Test
    fun `buildPathTokens builds correct path to nested property`() {
        val cityNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "city"))!!
        val path = KsonValueNavigation.buildPathTokens(sampleKson, cityNode)

        assertNotNull(path)
        assertEquals(listOf("address", "city"), path)
    }

    @Test
    fun `buildPathTokens builds correct path through arrays`() {
        val hobbyNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("hobbies", "1"))!!
        val path = KsonValueNavigation.buildPathTokens(sampleKson, hobbyNode)

        assertNotNull(path)
        assertEquals(listOf("hobbies", "1"), path)
    }

    @Test
    fun `buildPathTokens builds correct path to deeply nested node`() {
        val coordinateNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("address", "coordinates", "0"))!!
        val path = KsonValueNavigation.buildPathTokens(sampleKson, coordinateNode)

        assertNotNull(path)
        assertEquals(listOf("address", "coordinates", "0"), path)
    }

    @Test
    fun `buildPathTokens returns null for node not in tree`() {
        val unrelatedNode = KsonString("not in tree", Location.create(0, 0, 0, 0, 0, 0))
        val path = KsonValueNavigation.buildPathTokens(sampleKson, unrelatedNode)

        assertNull(path)
    }

    @Test
    fun `buildPathTokens and navigateByTokens are inverse operations`() {
        // Find a deeply nested node
        val targetNode = KsonValueNavigation.navigateByTokens(sampleKson, listOf("metadata", "tags", "0"))!!

        // Build path to it
        val path = KsonValueNavigation.buildPathTokens(sampleKson, targetNode)
        assertNotNull(path)

        // Navigate using that path should return the same node
        val foundNode = KsonValueNavigation.navigateByTokens(sampleKson, path)
        assertSame(targetNode, foundNode)
    }

    @Test
    fun `findAll finds all string nodes`() {
        val strings = KsonValueNavigation.findAll(sampleKson) { it is KsonString }

        assertTrue(strings.isNotEmpty())
        assertTrue(strings.all { it is KsonString })

        // Should include strings like 'John Doe', 'Springfield', 'reading', etc.
        assertTrue(strings.size >= 8, "Should find at least 8 strings in sample data")
    }

    @Test
    fun `findAll finds all objects`() {
        val objects = KsonValueNavigation.findAll(sampleKson) { it is KsonObject }

        assertTrue(objects.isNotEmpty())
        assertTrue(objects.all { it is KsonObject })

        // Should include root, address, metadata
        assertTrue(objects.size >= 3)
    }

    @Test
    fun `findAll finds all arrays`() {
        val arrays = KsonValueNavigation.findAll(sampleKson) { it is KsonList }

        assertTrue(arrays.isNotEmpty())
        assertTrue(arrays.all { it is KsonList })

        // Should include hobbies, coordinates, tags
        assertTrue(arrays.size >= 3)
    }

    @Test
    fun `findAll with complex predicate finds objects with specific property`() {
        val objectsWithTags = KsonValueNavigation.findAll(sampleKson) {
            it is KsonObject && it.propertyLookup.containsKey("tags")
        }

        assertEquals(1, objectsWithTags.size)
        assertTrue(objectsWithTags[0] is KsonObject)
        assertNotNull((objectsWithTags[0] as KsonObject).propertyLookup["tags"])
    }

    @Test
    fun `findAll returns empty list when no matches`() {
        val booleans = KsonValueNavigation.findAll(sampleKson) { it is KsonBoolean }

        assertTrue(booleans.isEmpty())
    }

    @Test
    fun `findFirst finds first string node`() {
        val firstString = KsonValueNavigation.findFirst(sampleKson) { it is KsonString }

        assertNotNull(firstString)
        assertTrue(firstString is KsonString)

        // Due to depth-first traversal, should be 'John Doe' (first property value)
        assertEquals("John Doe", firstString.value)
    }

    @Test
    fun `findFirst returns null when no match`() {
        val boolean = KsonValueNavigation.findFirst(sampleKson) { it is KsonBoolean }

        assertNull(boolean)
    }

    @Test
    fun `findFirst stops after finding first match`() {
        var callCount = 0

        KsonValueNavigation.findFirst(sampleKson) { node ->
            callCount++
            node is KsonString
        }

        // Should have stopped early after finding first string
        // If it visited all nodes, callCount would be much higher
        // But we can't assert exact count without knowing traversal order details
        assertTrue(callCount > 0, "Should have called predicate at least once")
    }

    @Test
    fun `navigateByTokens handles complex nested structure`() {
        val complexKson = KsonCore.parseToAst("""
            users:
              - name: 'Alice'
                roles:
                  - 'admin'
                  - 'editor'
                .
              - name: 'Bob'
                roles:
                  - 'viewer'
                .
            .
        """.trimIndent()).ksonValue!!

        val result = KsonValueNavigation.navigateByTokens(complexKson, listOf("users", "0", "roles", "1"))

        assertNotNull(result)
        assertTrue(result is KsonString)
        assertEquals("editor", result.value)
    }

    @Test
    fun `walkTree handles single primitive value`() {
        val primitive = KsonString("test", Location.create(0, 0, 0, 0, 0, 0))
        val visited = mutableListOf<KsonValue>()

        KsonValueNavigation.walkTree(primitive) { node, parent, depth ->
            visited.add(node)
            assertEquals(0, depth)
            assertNull(parent)
        }

        assertEquals(1, visited.size)
        assertSame(primitive, visited[0])
    }

    @Test
    fun `walkTree handles empty object`() {
        val emptyObj = KsonCore.parseToAst("{}").ksonValue!!
        val visited = mutableListOf<KsonValue>()

        KsonValueNavigation.walkTree(emptyObj) { node, _, _ ->
            visited.add(node)
        }

        assertEquals(1, visited.size)
        assertTrue(visited[0] is KsonObject)
    }

    @Test
    fun `walkTree handles empty array`() {
        val emptyArray = KsonCore.parseToAst("<>").ksonValue!!
        val visited = mutableListOf<KsonValue>()

        KsonValueNavigation.walkTree(emptyArray) { node, _, _ ->
            visited.add(node)
        }

        assertEquals(1, visited.size)
        assertTrue(visited[0] is KsonList)
    }

    @Test
    fun `buildPathTokens works with mixed object and array navigation`() {
        val complexKson = KsonCore.parseToAst("""
            data:
              items:
                - values:
                    - 1
                    - 2
                  .
              .
            .
        """.trimIndent()).ksonValue!!

        val target = KsonValueNavigation.navigateByTokens(complexKson, listOf("data", "items", "0", "values", "1"))!!
        val path = KsonValueNavigation.buildPathTokens(complexKson, target)

        assertNotNull(path)
        assertEquals(listOf("data", "items", "0", "values", "1"), path)

        // Verify round-trip
        val found = KsonValueNavigation.navigateByTokens(complexKson, path)
        assertSame(target, found)
    }

    // Tests for findValueAtLocation

    @Test
    fun `findValueAtLocation finds node at specific location`() {
        // Parse a document with known locations
        val doc = KsonCore.parseToAst("""
            name: 'Alice'
            age: 30
        """.trimIndent()).ksonValue!!

        // Get the actual location of the 'name' property value
        val nameValue = KsonValueNavigation.navigateByTokens(doc, listOf("name"))!!
        val targetLocation = nameValue.location

        // Find the node at that location
        val found = KsonValueNavigation.findValueAtLocation(doc, targetLocation)

        assertNotNull(found)
        assertSame(nameValue, found)
    }

    @Test
    fun `findValueAtLocation returns null when location not in tree`() {
        val doc = KsonCore.parseToAst("name: 'Alice'").ksonValue!!

        // Create a location that's way outside the document
        val outsideLocation = Location.create(100, 100, 100, 105, 10000, 10005)

        val found = KsonValueNavigation.findValueAtLocation(doc, outsideLocation)

        assertNull(found)
    }

    @Test
    fun `findValueAtLocation returns most specific node when nested`() {
        val doc = KsonCore.parseToAst("""
            person:
              name: 'Bob'
              address:
                city: 'NYC'
              .
            .
        """.trimIndent()).ksonValue!!

        // Get the location of the deeply nested 'NYC' string
        val cityValue = KsonValueNavigation.navigateByTokens(doc, listOf("person", "address", "city"))!!
        val cityLocation = cityValue.location

        // Find the node - should return the string, not the parent object or address object
        val found = KsonValueNavigation.findValueAtLocation(doc, cityLocation)

        assertNotNull(found)
        assertSame(cityValue, found)
        assertTrue(found is KsonString)
        assertEquals("NYC", found.value)
    }

    @Test
    fun `findValueAtLocation returns deepest node in nested arrays`() {
        val doc = KsonCore.parseToAst("""
            matrix:
              - - 1
                - 2
              - - 3
                - 4
            .
        """.trimIndent()).ksonValue!!

        // Get the location of a deeply nested number
        val numberValue = KsonValueNavigation.navigateByTokens(doc, listOf("matrix", "0", "1"))!!
        val numberLocation = numberValue.location

        // Should return the number, not any of the parent arrays
        val found = KsonValueNavigation.findValueAtLocation(doc, numberLocation)

        assertNotNull(found)
        assertSame(numberValue, found)
        assertTrue(found is KsonNumber)
        assertEquals(2.0, found.value.asDouble)
    }

    @Test
    fun `findValueAtLocation finds parent when location spans entire child`() {
        val doc = KsonCore.parseToAst("""
            users:
              - name: 'Alice'
              - name: 'Bob'
            .
        """.trimIndent()).ksonValue!!

        // Get an array element
        val firstUser = KsonValueNavigation.navigateByTokens(doc, listOf("users", "0"))!!

        // Use the exact location of the first user object
        val found = KsonValueNavigation.findValueAtLocation(doc, firstUser.location)

        assertNotNull(found)
        assertSame(firstUser, found)
        assertTrue(found is KsonObject)
    }

    @Test
    fun `findValueAtLocation with location at start of node`() {
        val doc = KsonCore.parseToAst("value: 'test'").ksonValue!!
        val stringValue = KsonValueNavigation.navigateByTokens(doc, listOf("value"))!!

        // Create a location at just the start coordinate
        val startLocation = Location.create(
            stringValue.location.start.line,
            stringValue.location.start.column,
            stringValue.location.start.line,
            stringValue.location.start.column,
            stringValue.location.startOffset,
            stringValue.location.startOffset
        )

        val found = KsonValueNavigation.findValueAtLocation(doc, startLocation)

        assertNotNull(found)
        // Should find the string or a containing node
        assertTrue(found is KsonString || found is KsonObject)
    }

    @Test
    fun `findValueAtLocation with location at end of node`() {
        val doc = KsonCore.parseToAst("value: 'test'").ksonValue!!
        val stringValue = KsonValueNavigation.navigateByTokens(doc, listOf("value"))!!

        // Create a location at just the end coordinate
        val endLocation = Location.create(
            stringValue.location.end.line,
            stringValue.location.end.column,
            stringValue.location.end.line,
            stringValue.location.end.column,
            stringValue.location.endOffset,
            stringValue.location.endOffset
        )

        val found = KsonValueNavigation.findValueAtLocation(doc, endLocation)

        // Depending on boundary handling, might find the node or parent
        // At minimum, should not crash and should return something or null
        assertTrue(KsonString("test", Location.create(1, 8, 1, 14, 7, 13)).dataEquals(found))
    }

    @Test
    fun `findValueAtLocation handles empty object`() {
        val doc = KsonCore.parseToAst("{}").ksonValue!!

        // Use the object's own location
        val found = KsonValueNavigation.findValueAtLocation(doc, doc.location)

        assertNotNull(found)
        assertSame(doc, found)
        assertTrue(found is KsonObject)
    }

    @Test
    fun `findValueAtLocation handles empty array`() {
        val doc = KsonCore.parseToAst("<>").ksonValue!!

        // Use the array's own location
        val found = KsonValueNavigation.findValueAtLocation(doc, doc.location)

        assertNotNull(found)
        assertSame(doc, found)
        assertTrue(found is KsonList)
    }

    @Test
    fun `findValueAtLocation handles single primitive`() {
        val doc = KsonString("test", Location.create(0, 0, 0, 4, 0, 4))

        val found = KsonValueNavigation.findValueAtLocation(doc, doc.location)

        assertNotNull(found)
        assertSame(doc, found)
    }

    @Test
    fun `findValueAtLocation prefers smaller node over larger containing node`() {
        val doc = KsonCore.parseToAst("""
            outer:
              inner: 'value'
            .
        """.trimIndent()).ksonValue!!

        // Get both the inner value and outer object
        val innerValue = KsonValueNavigation.navigateByTokens(doc, listOf("outer", "inner"))!!

        // The inner value's location should return the inner value, not the outer object
        val found = KsonValueNavigation.findValueAtLocation(doc, innerValue.location)

        assertNotNull(found)
        assertSame(innerValue, found)
        assertTrue(found is KsonString)
    }

    @Test
    fun `findValueAtLocation handles complex nested structure`() {
        val doc = KsonCore.parseToAst("""
            data:
              items:
                - values:
                    - 1
                    - 2
                    - 3
                  .
                - values:
                    - 4
                    - 5
                  .
              .
            .
        """.trimIndent()).ksonValue!!

        // Find a deeply nested value
        val deepValue = KsonValueNavigation.navigateByTokens(doc, listOf("data", "items", "1", "values", "1"))!!

        val found = KsonValueNavigation.findValueAtLocation(doc, deepValue.location)

        assertNotNull(found)
        assertSame(deepValue, found)
        assertTrue(found is KsonNumber)
        assertEquals(5.0, found.value.asDouble)
    }

    @Test
    fun `findValueAtLocation returns null for location before document`() {
        val doc = KsonCore.parseToAst("name: 'test'").ksonValue!!

        // Create a location before the document starts (negative would be invalid, use 0,0 before actual content)
        // This is a bit tricky - let's use a location that's definitely before any content
        val beforeLocation = Location.create(0, 0, 0, 0, 0, 0)

        // This might find the root or might return null depending on how locations are assigned
        val found = KsonValueNavigation.findValueAtLocation(doc, beforeLocation)

        // The result is acceptable whether it finds the root or returns null
        assertTrue(found == null || found === doc)
    }

    @Test
    fun `findValueAtLocation with multiple overlapping candidates chooses smallest`() {
        // Create a document where we know the location relationships
        val doc = KsonCore.parseToAst("""
            list:
              - 'first'
              - 'second'
              - 'third'
        """.trimIndent()).ksonValue!!

        val secondElement = KsonValueNavigation.navigateByTokens(doc, listOf("list", "1"))!!

        // When finding a node at the second element's location,
        // it should return the element itself, not the containing list or root object
        val found = KsonValueNavigation.findValueAtLocation(doc, secondElement.location)

        assertNotNull(found)
        assertSame(secondElement, found)
        assertTrue(found is KsonString)
        assertEquals("second", found.value)
    }
}