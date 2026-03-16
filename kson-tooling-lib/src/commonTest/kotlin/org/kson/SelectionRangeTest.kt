package org.kson

import kotlin.test.*

class SelectionRangeTest {

    @Test
    fun testEmptyDocumentReturnsEmpty() {
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(""), 0, 0)
        assertEquals(0, ranges.size)
    }

    @Test
    fun testSimpleStringValue() {
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse("\"hello\""), 0, 2)
        assertTrue(ranges.isNotEmpty(), "Should have at least one range for string value")
    }

    @Test
    fun testObjectPropertyValue() {
        val content = listOf(
            "{",
            "  name: \"Alice\"",
            "  age: 30",
            "}"
        ).joinToString("\n")

        // Cursor on "Alice" string value (line 1, inside the string)
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 1, 10)

        // Should have multiple levels: string -> property -> object
        assertTrue(ranges.size >= 3, "Expected at least 3 levels, got ${ranges.size}")

        // Outermost should be the object range
        val outermost = ranges.last()
        assertEquals(0, outermost.startLine)
        assertEquals(0, outermost.startColumn)
    }

    @Test
    fun testCursorOnPropertyKey() {
        val content = listOf(
            "{",
            "  name: \"Alice\"",
            "}"
        ).joinToString("\n")

        // Cursor on "name" key (line 1, character 3)
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 1, 3)

        // Should have: key -> property -> object
        assertTrue(ranges.size >= 3, "Expected at least 3 levels, got ${ranges.size}")
    }

    @Test
    fun testNestedObjects() {
        val content = listOf(
            "{",
            "  person: {",
            "    name: \"Alice\"",
            "  }",
            "}"
        ).joinToString("\n")

        // Cursor on "Alice" (line 2, character 12)
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 2, 12)

        // Should have: string -> property (name:Alice) -> inner object -> property (person:{...}) -> outer object
        assertTrue(ranges.size >= 4, "Expected at least 4 levels for nested object, got ${ranges.size}")
    }

    @Test
    fun testArrayElements() {
        val content = listOf(
            "[",
            "  \"one\"",
            "  \"two\"",
            "  \"three\"",
            "]"
        ).joinToString("\n")

        // Cursor on "two" (line 2, character 3)
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 2, 3)

        // Should have: string -> array
        assertTrue(ranges.size >= 2, "Expected at least 2 levels for array element, got ${ranges.size}")
    }

    @Test
    fun testCursorOnContainerDelimiter() {
        val content = listOf(
            "{",
            "  name: \"Alice\"",
            "}"
        ).joinToString("\n")

        // Cursor on opening brace (line 0, character 0)
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 0, 0)

        // Should have: object range
        assertTrue(ranges.isNotEmpty())
    }

    @Test
    fun testStrictlyExpandingChain() {
        val content = listOf(
            "{",
            "  items: [",
            "    \"hello\"",
            "  ]",
            "}"
        ).joinToString("\n")

        // Cursor on "hello" (line 2, character 6)
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 2, 6)

        // Verify each range is contained within (or equal to) the next
        for (i in 0 until ranges.size - 1) {
            val inner = ranges[i]
            val outer = ranges[i + 1]

            val innerStartsBefore = inner.startLine < outer.startLine ||
                (inner.startLine == outer.startLine && inner.startColumn < outer.startColumn)
            val innerEndsAfter = inner.endLine > outer.endLine ||
                (inner.endLine == outer.endLine && inner.endColumn > outer.endColumn)

            assertFalse(innerStartsBefore,
                "Level $i starts before level ${i + 1}: (${inner.startLine}:${inner.startColumn}) vs (${outer.startLine}:${outer.startColumn})")
            assertFalse(innerEndsAfter,
                "Level $i ends after level ${i + 1}: (${inner.endLine}:${inner.endColumn}) vs (${outer.endLine}:${outer.endColumn})")
        }
    }

    @Test
    fun testDeduplication() {
        // When a node and its parent have the same range, the duplicate should be removed
        val content = "\"hello\""
        val ranges = KsonTooling.getEnclosingRanges(KsonTooling.parse(content), 0, 2)

        // Verify no adjacent ranges are identical
        for (i in 0 until ranges.size - 1) {
            val a = ranges[i]
            val b = ranges[i + 1]
            val equal = a.startLine == b.startLine && a.startColumn == b.startColumn
                && a.endLine == b.endLine && a.endColumn == b.endColumn
            assertFalse(equal, "Adjacent ranges at index $i and ${i + 1} should not be identical")
        }
    }
}
