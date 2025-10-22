@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.navigation.SchemaNavigation
import org.kson.value.KsonValueNavigation
import org.kson.parser.Location
import org.kson.parser.Coordinates
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Tooling utilities for IDE features like hover information and completions.
 *
 * This module provides schema-aware navigation and introspection capabilities
 * for building IDE integrations.
 */
object KsonTooling {

    /**
     * Get schema hover information for a position in a document.
     *
     * This is a convenience method that finds the KsonValue at the given position
     * and then retrieves schema hover information for it.
     *
     * @param documentRoot The root of the document being edited (internal KsonValue)
     * @param schemaValue The schema for the document (internal KsonValue)
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return Formatted hover text, or null if no schema info available
     */
    fun getSchemaInfoAtLocation(
        documentRoot: String,
        schemaValue: String,
        line: Int,
        column: Int
    ): String? {
        // Parse the document to get internal representation with proper locations
        val documentRootValue = KsonCore.parseToAst(documentRoot).ksonValue ?: return null

        // Create a location from the position
        val location = Location(
            start = Coordinates(line, column),
            end = Coordinates(line, column),
            startOffset = 0,
            endOffset = 0
        )

        // Step 1: Find the KsonValue at the given location using KsonValueNavigation
        val targetNode = KsonValueNavigation.findValueAtLocation(documentRootValue, location) ?: return null

        // Step 2: Use existing getSchemaHoverInfo logic
        val schemaInfo = KsonCore.parseToAst(schemaValue).ksonValue.let{
            it ?: return null
            SchemaNavigation.getSchemaInfo(documentRootValue, targetNode, it)
        }

        return schemaInfo
    }

    /**
     * Get completion suggestions for a position in a document.
     *
     * This is a convenience method that finds the KsonValue at the given position
     * and then retrieves completion suggestions based on the schema.
     *
     * @param documentRoot The root of the document being edited (KSON string)
     * @param schemaValue The schema for the document (KSON string)
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return List of completion items, or null if no completions available
     */
    fun getCompletionsAtLocation(
        documentRoot: String,
        schemaValue: String,
        line: Int,
        column: Int
    ): List<CompletionItem>? {
        // If cursor is at the last token, append "" to ensure valid document
        val modifiedDocument = if (isCursorAtLastToken(documentRoot, line, column)) {
            documentRoot + "\"\""
        } else {
            documentRoot
        }

        // Parse the document to get internal representation with proper locations
        val documentRootValue = KsonCore.parseToAst(modifiedDocument).ksonValue ?: return null

        // Create a location from the position
        val location = Location(
            start = Coordinates(line, column),
            end = Coordinates(line, column),
            startOffset = 0,
            endOffset = 0
        )

        // Step 1: Find the KsonValue at the given location using KsonValueNavigation
        val targetNode = KsonValueNavigation.findValueAtLocation(documentRootValue, location) ?: return null

        // Step 2: Get completions
        val completions = KsonCore.parseToAst(schemaValue).ksonValue?.let{
            SchemaNavigation.getCompletions(documentRootValue, targetNode, it)
        } ?: return null

        return completions.takeIf { it.isNotEmpty() }
    }

    /**
     * Check if the cursor is at the last token in the document.
     *
     * @param document The document text
     * @param line The zero-based line number
     * @param column The zero-based column number
     * @return True if cursor is at or after the last non-whitespace token
     */
    private fun isCursorAtLastToken(document: String, line: Int, column: Int): Boolean {
        val lines = document.lines()
        if (line >= lines.size) {
            return true
        }

        // Check if there's any non-whitespace content after the cursor position
        val targetLine = lines[line]
        val afterCursor = targetLine.substring(column.coerceAtMost(targetLine.length))

        // Check rest of current line
        if (afterCursor.isNotBlank()) {
            return false
        }

        // Check all lines after current line
        for (i in line + 1 until lines.size) {
            if (lines[i].isNotBlank()) {
                return false
            }
        }

        return true
    }
}

/**
 * Represents a completion item to be shown in the IDE.
 */
class CompletionItem(
    val label: String,              // The text to insert
    val detail: String?,            // Short description (e.g., "string")
    val documentation: String?,     // Full markdown documentation
    val kind: CompletionKind        // Type of completion
)

/**
 * The type of completion item.
 */
enum class CompletionKind {
    PROPERTY,    // Object property name
    VALUE        // Enum value or suggested value
}