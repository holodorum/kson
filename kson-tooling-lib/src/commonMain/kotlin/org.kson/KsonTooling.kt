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
}