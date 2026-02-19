package org.kson

import kotlin.test.*

class FoldingRangeTest {

    @Test
    fun testSingleLineDocumentHasNoRanges() {
        val ranges = KsonTooling.getStructuralRanges("key: value")
        assertEquals(0, ranges.size)
    }

    @Test
    fun testMultiLineObject() {
        val content = listOf(
            "{",
            "  name: \"Alice\"",
            "  age: 30",
            "}"
        ).joinToString("\n")
        val ranges = KsonTooling.getStructuralRanges(content)

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(3, ranges[0].endLine)
        assertEquals(StructuralRangeKind.OBJECT, ranges[0].kind)
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
        val ranges = KsonTooling.getStructuralRanges(content)

        assertEquals(2, ranges.size)
        // Inner object folds from line 1 to line 3
        val innerRange = ranges.find { it.startLine == 1 }
        assertNotNull(innerRange)
        assertEquals(3, innerRange.endLine)
        assertEquals(StructuralRangeKind.OBJECT, innerRange.kind)
        // Outer object folds from line 0 to line 4
        val outerRange = ranges.find { it.startLine == 0 }
        assertNotNull(outerRange)
        assertEquals(4, outerRange.endLine)
        assertEquals(StructuralRangeKind.OBJECT, outerRange.kind)
    }

    @Test
    fun testMultiLineArray() {
        val content = listOf(
            "[",
            "  1",
            "  2",
            "  3",
            "]"
        ).joinToString("\n")
        val ranges = KsonTooling.getStructuralRanges(content)

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(4, ranges[0].endLine)
        assertEquals(StructuralRangeKind.ARRAY, ranges[0].kind)
    }

    @Test
    fun testEmbedBlock() {
        val content = listOf(
            "query: \$sql",
            "  SELECT *",
            "  FROM users",
            "  \$\$"
        ).joinToString("\n")
        val ranges = KsonTooling.getStructuralRanges(content)

        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].startLine)
        assertEquals(3, ranges[0].endLine)
        assertEquals(StructuralRangeKind.EMBED, ranges[0].kind)
    }

    @Test
    fun testSingleLineObjectHasNoRanges() {
        val ranges = KsonTooling.getStructuralRanges("{ name: \"Alice\", age: 30 }")
        assertEquals(0, ranges.size)
    }

    @Test
    fun testEmptyDocument() {
        val ranges = KsonTooling.getStructuralRanges("")
        assertEquals(0, ranges.size)
    }

    @Test
    fun testMixedFoldableRegions() {
        val content = listOf(
            "{",
            "  items: [",
            "    \"one\"",
            "    \"two\"",
            "  ]",
            "  code: \$js",
            "    console.log(\"hi\")",
            "    \$\$",
            "}"
        ).joinToString("\n")
        val ranges = KsonTooling.getStructuralRanges(content)

        // Should have 3 foldable regions: outer {}, inner [], embed
        assertEquals(3, ranges.size)
    }
}
