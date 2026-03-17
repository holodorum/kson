package org.kson.jetbrains.schema

/** Convert a 0-based offset in [text] to a (line, column) pair (both 0-based). */
internal fun lineColumnFromOffset(text: String, offset: Int): Pair<Int, Int>? {
    if (offset < 0 || offset > text.length) return null
    var line = 0
    var col = 0
    for (i in 0 until offset) {
        if (text[i] == '\n') { line++; col = 0 } else col++
    }
    return line to col
}

/** Convert a 0-based (line, column) in [text] to a 0-based offset. */
internal fun offsetFromLineColumn(text: String, line: Int, column: Int): Int? {
    var currentLine = 0
    var i = 0
    while (i < text.length && currentLine < line) {
        if (text[i] == '\n') currentLine++
        i++
    }
    if (currentLine != line) return null
    val targetOffset = i + column
    return if (targetOffset <= text.length) targetOffset else null
}
