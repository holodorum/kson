@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.navigation.SchemaNavigation
import org.kson.value.KsonValueNavigation
import org.kson.parser.Location
import org.kson.parser.Coordinates
import org.kson.parser.TokenType
import org.kson.value.KsonObject
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
        val buildPath = buildPathToPosition(documentRoot, Coordinates(line, column)) ?: return null
        val schemaInfo = KsonCore.parseToAst(schemaValue).ksonValue.let{
            it ?: return null
            SchemaNavigation.getSchemaInfo(it, buildPath)
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
        // Create a location from the position
        val position = Coordinates(line, column)

        val buildPath = buildPathToPosition(documentRoot, position) ?: return null

        // Step 2: Get completions
        val completions = KsonCore.parseToAst(schemaValue).ksonValue?.let{
            SchemaNavigation.getCompletions(it, buildPath)
        } ?: return null

        return completions.takeIf { it.isNotEmpty() }
    }

    /**
     * Builds a path from the document root to the target location.
     *
     * This method analyzes the document structure and cursor position to determine
     * the JSON path (sequence of property names) from the root to the element at
     * the given location. This path is used for schema navigation and IDE features.
     *
     * The method handles several edge cases:
     * - Invalid documents (attempts recovery by inserting quotes)
     * - Cursor positioned after a colon (targets the value being entered)
     * - Cursor positioned outside a token (targets the parent element)
     *
     * @param document The KSON document string
     * @param location The cursor position (line and column, zero-based)
     * @return A list of property names representing the path from root to target,
     *         or null if the path cannot be determined
     */
    private fun buildPathToPosition(document: String, location: Coordinates): List<String>? {
        val parsedDocument = KsonCore.parseToAst(document)

        // Find the token immediately before or at the cursor position
        val lastToken = findLastTokenBeforeCursor(parsedDocument.lexedTokens, location)

        // Check if the cursor is actually inside the token's bounds
        val isCursorInsideToken = isPositionInsideToken(lastToken, location)

        // Parse the document, or attempt recovery if it contains syntax errors
        val documentValue = parsedDocument.ksonValue
            ?: attemptDocumentRecovery(document, location)
            ?: return null

        // Determine the search position: use token start if available, otherwise cursor position
        val searchPosition = lastToken?.lexeme?.location?.start ?: location

        // Navigate to the KsonValue node at the search position
        val targetNode = KsonValueNavigation.findValueAtPosition(documentValue, searchPosition)
            ?: return null

        // Build the initial path from root to the target node
        val initialPath = KsonValueNavigation.buildPathTokens(documentValue, targetNode)
            ?: return emptyList()

        // Adjust the path based on cursor context (colon handling, boundary checks)
        return adjustPathForCursorContext(
            path = initialPath,
            lastToken = lastToken,
            targetNode = targetNode,
            isCursorInsideToken = isCursorInsideToken
        )
    }

    /**
     * Finds the last token that starts at or before the cursor location.
     *
     * This helps determine what syntactic element the cursor is positioned at or after.
     * The EOF token is excluded from consideration.
     *
     * @param tokens The list of lexed tokens from the document
     * @param location The cursor position
     * @return The last token before the cursor, or null if no such token exists
     */
    private fun findLastTokenBeforeCursor(
        tokens: List<org.kson.parser.Token>,
        location: Coordinates
    ): org.kson.parser.Token? {
        return tokens
            .dropLast(1)  // Exclude EOF token
            .lastOrNull { token ->
                val tokenStart = token.lexeme.location.start
                // Token starts before or at the cursor location
                tokenStart.line < location.line ||
                    (tokenStart.line == location.line && tokenStart.column <= location.column)
            }
    }

    /**
     * Checks if the given position falls within the bounds of a token.
     *
     * @param token The token to check, or null
     * @param position The position to test
     * @return true if the position is inside the token's location, false otherwise
     */
    private fun isPositionInsideToken(
        token: org.kson.parser.Token?,
        position: Coordinates
    ): Boolean {
        return token?.lexeme?.location?.let {
            Location.locationContainsPosition(it, position)
        } ?: false
    }

    /**
     * Adjusts the path based on cursor context.
     *
     * Handles two special cases:
     * 1. Cursor after a colon: Add the property name to target the value being entered
     * 2. Cursor outside token bounds: Remove the last path element to target the parent
     *
     * @param path The initial path built from document navigation
     * @param lastToken The last token before the cursor
     * @param targetNode The KsonValue node found at the target location
     * @param isCursorInsideToken Whether the cursor is inside the token bounds
     * @return The adjusted path
     */
    private fun adjustPathForCursorContext(
        path: List<String>,
        lastToken: org.kson.parser.Token?,
        targetNode: org.kson.value.KsonValue,
        isCursorInsideToken: Boolean
    ): List<String> {
        return when {
            // Cursor is right after a colon - we're entering a value
            lastToken?.tokenType == TokenType.COLON -> {
                val propertyName = (targetNode as KsonObject).propertyLookup.keys.last()
                path + propertyName
            }
            // Cursor is outside the token - target the parent element
            !isCursorInsideToken -> {
                path.dropLast(1)
            }
            // Normal case - return path as-is
            else -> path
        }
    }

    /**
     * Attempts to recover a parseable document from an invalid one.
     *
     * When a document contains syntax errors, this method tries to make it valid
     * by inserting empty quotes at the cursor position. This is useful for
     * providing IDE features even when the user is in the middle of typing.
     *
     * For example, if the cursor is at `{ "key": | }`, this would try parsing
     * `{ "key": "" }` to enable completions for the value.
     *
     * @param document The invalid document string
     * @param location The cursor position where quotes should be inserted
     * @return A KsonValue from the recovered document, or null if recovery fails
     */
    private fun attemptDocumentRecovery(
        document: String,
        location: Coordinates
    ): org.kson.value.KsonValue? {
        val lines = document.lines().toMutableList()

        // Validate that the target line exists
        if (location.line >= lines.size) {
            return null
        }

        val targetLine = lines[location.line]
        val safeColumn = location.column.coerceAtMost(targetLine.length)

        // Insert empty quotes at the cursor position
        val recoveredLine = buildString {
            append(targetLine.take(safeColumn))
            append("\"\"")  // Empty string literal
            append(targetLine.substring(safeColumn))
        }

        lines[location.line] = recoveredLine
        val recoveredDocument = lines.joinToString("\n")

        // Attempt to parse the recovered document
        return KsonCore.parseToAst(recoveredDocument).ksonValue
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