package org.kson

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.value.*

/**
 * Builds a list of enclosing [Range]s for a given cursor position in KSON source.
 *
 * Walks the [KsonValue] AST to collect all ranges that contain the cursor,
 * from innermost to outermost. Used for smart expand/shrink selection.
 */
internal object SelectionRangeBuilder {

    fun build(ksonValue: KsonValue, line: Int, column: Int): List<Range> {
        val ancestors = mutableListOf<Range>()
        collectAncestors(ksonValue, Coordinates(line, column), ancestors)
        return deduplicate(ancestors)
    }

    /**
     * Recursively collect all AST node ranges that contain the given position,
     * from innermost to outermost.
     */
    private fun collectAncestors(value: KsonValue, cursor: Coordinates, ancestors: MutableList<Range>): Boolean {
        if (!Location.containsCoordinates(value.location, cursor)) {
            return false
        }

        val range = toRange(value)

        when (value) {
            is KsonObject -> {
                for ((_, prop) in value.propertyMap) {
                    val keyLoc = prop.propName.location
                    val valueLoc = prop.propValue.location

                    // Property range: from key start to value end
                    val propertyRange = Range(
                        keyLoc.start.line, keyLoc.start.column,
                        valueLoc.end.line, valueLoc.end.column,
                        keyLoc.startOffset, valueLoc.endOffset
                    )

                    if (containsPosition(propertyRange, cursor)) {
                        // Try to descend into the value first
                        val descendedIntoValue = collectAncestors(prop.propValue, cursor, ancestors)

                        if (!descendedIntoValue) {
                            // Cursor is on the key itself
                            val keyRange = toRange(prop.propName)
                            if (containsPosition(keyRange, cursor)) {
                                ancestors.add(keyRange)
                            }
                        }

                        // Add property range (key + value)
                        ancestors.add(propertyRange)

                        // Add the containing object
                        ancestors.add(range)
                        return true
                    }
                }
            }

            is KsonList -> {
                for (element in value.elements) {
                    if (collectAncestors(element, cursor, ancestors)) {
                        ancestors.add(range)
                        return true
                    }
                }
            }

            else -> {}
        }

        // Leaf node or cursor is on container delimiters (e.g. { or })
        ancestors.add(range)
        return true
    }

    /**
     * Deduplicate adjacent ranges that are identical.
     */
    private fun deduplicate(ranges: List<Range>): List<Range> {
        if (ranges.isEmpty()) return ranges
        val result = mutableListOf(ranges[0])
        for (i in 1 until ranges.size) {
            if (!rangesEqual(ranges[i], result.last())) {
                result.add(ranges[i])
            }
        }
        return result
    }

    private fun containsPosition(range: Range, cursor: Coordinates): Boolean {
        if (cursor.line < range.startLine || cursor.line > range.endLine) return false
        if (cursor.line == range.startLine && cursor.column < range.startColumn) return false
        if (cursor.line == range.endLine && cursor.column > range.endColumn) return false
        return true
    }

    private fun rangesEqual(a: Range, b: Range): Boolean {
        return a.startLine == b.startLine
            && a.startColumn == b.startColumn
            && a.endLine == b.endLine
            && a.endColumn == b.endColumn
    }

    private fun toRange(value: KsonValue): Range {
        return Range(
            value.location.start.line,
            value.location.start.column,
            value.location.end.line,
            value.location.end.column,
            value.location.startOffset, value.location.endOffset,
        )
    }
}
