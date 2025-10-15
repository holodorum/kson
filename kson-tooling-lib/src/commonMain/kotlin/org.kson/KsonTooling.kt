package org.kson

import org.kson.value.*
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup

/**
 * Tooling utilities for IDE features like hover information and completions.
 *
 * This module provides schema-aware navigation and introspection capabilities
 * for building IDE integrations.
 */
object KsonTooling {

    /**
     * Navigate a schema (as KsonValue) to find the sub-schema corresponding to a document path.
     *
     * This function translates document paths to schema paths by inserting schema-specific wrappers:
     * - For object properties: navigates through "properties" wrapper
     * - For array indices: navigates to "items" schema (all array elements share the same schema)
     * - Falls back to "additionalProperties" or "patternProperties" when specific property not found
     *
     * Example:
     * ```kotlin
     * // Document path: ["users", "0", "name"]
     * // Schema navigation: properties/users � items � properties/name
     * val schemaRef = navigateSchemaByDocumentPath(schemaValue, listOf("users", "0", "name"))
     * ```
     *
     * @param schemaValue The root schema as a [KsonValue] (typically parsed from JSON Schema)
     * @param documentPathTokens Path through the document (from [KsonValueNavigation.buildPathTokens])
     * @param currentBaseUri The current base URI for resolving `$id` references (default: empty)
     * @return The [ResolvedRef] containing the sub-schema at that location, or null if not found
     */
    fun navigateSchemaByDocumentPath(
        schemaValue: KsonValue,
        documentPathTokens: List<String>,
        currentBaseUri: String = ""
    ): ResolvedRef? {
        if (documentPathTokens.isEmpty()) {
            return ResolvedRef(schemaValue, currentBaseUri)
        }

        var node: KsonValue? = schemaValue
        var updatedBaseUri = currentBaseUri

        for (token in documentPathTokens) {
            if (node !is KsonObject) {
                return null
            }

            // Track $id changes for proper URI resolution
            node.propertyLookup[$$"$id"]?.let { idValue ->
                if (idValue is KsonString) {
                    val fullyQualifiedId = SchemaIdLookup.resolveUri(idValue.value, updatedBaseUri)
                    updatedBaseUri = fullyQualifiedId.toString()
                }
            }

            // Determine if this is an array index or property name
            val isArrayIndex = token.toIntOrNull() != null

            node = if (isArrayIndex) {
                // Array navigation: go to "items" schema
                // We ignore the actual index - all array elements use the same schema
                navigateArrayItems(node)
            } else {
                // Object navigation: go to "properties" wrapper, then the property
                navigateObjectProperty(node, token)
            }

            if (node == null) {
                break
            }
        }

        return node?.let { ResolvedRef(it, updatedBaseUri) }
    }

    /**
     * Navigate to the schema for array items.
     *
     * Looks for "items" or "additionalItems" schema properties.
     */
    private fun navigateArrayItems(schemaNode: KsonObject): KsonValue? {
        // Try "items" first (most common case)
        schemaNode.propertyLookup["items"]?.let { return it }

        // Fallback to "additionalItems"
        return schemaNode.propertyLookup["additionalItems"]
    }

    /**
     * Navigate an object schema to find the sub-schema for a property.
     *
     * Handles multiple JSON Schema patterns:
     * 1. Direct property lookup in "properties"
     * 2. Pattern matching via "patternProperties"
     * 3. Fallback to "additionalProperties"
     */
    private fun navigateObjectProperty(schemaNode: KsonObject, propertyName: String): KsonValue? {
        // Try direct property lookup in "properties"
        val properties = schemaNode.propertyLookup["properties"] as? KsonObject
        properties?.propertyLookup?.get(propertyName)?.let { return it }

        // Try pattern properties - check all patterns for a match
        val patternProperties = schemaNode.propertyLookup["patternProperties"] as? KsonObject
        patternProperties?.propertyMap?.forEach { (pattern, property) ->
            try {
                if (Regex(pattern).containsMatchIn(propertyName)) {
                    return property.propValue
                }
            } catch (_: Exception) {
                // Invalid regex pattern, skip it
            }
        }

        // Fallback to additionalProperties
        return schemaNode.propertyLookup["additionalProperties"]
    }

    /**
     * Extract hover information from a schema node.
     *
     * Formats schema metadata into markdown suitable for IDE hover tooltips.
     * Includes: title, description, type, default value, constraints (enum, pattern, min/max).
     *
     * @param schemaNode The schema node to extract information from
     * @return Formatted markdown string, or null if no hover info available
     */
    fun extractSchemaHoverInfo(schemaNode: KsonValue): String? {
        if (schemaNode !is KsonObject) return null

        val props = schemaNode.propertyLookup

        return buildString {
            // Title (bold header)
            (props["title"] as? KsonString)?.value?.let {
                append("**$it**\n\n")
            }

            // Description (main documentation)
            (props["description"] as? KsonString)?.value?.let {
                append("$it\n\n")
            }

            // Type information
            when (val typeValue = props["type"]) {
                is KsonString -> {
                    append("*Type:* `${typeValue.value}`\n\n")
                }
                is KsonList -> {
                    // Union type: ["string", "number"]
                    val types = typeValue.elements.mapNotNull { (it as? KsonString)?.value }
                    if (types.isNotEmpty()) {
                        append("*Type:* `${types.joinToString(" | ")}`\n\n")
                    }
                }
                is KsonBoolean, is KsonNull, is KsonNumber, is KsonObject, is EmbedBlock, null -> {
                    // These types are not expected for the "type" property in a schema
                    // We simply don't add type information in these cases
                }
            }

            // Default value
            props["default"]?.let {
                append("*Default:* `${formatValueForDisplay(it)}`\n\n")
            }

            // Enum values
            (props["enum"] as? KsonList)?.let { enumList ->
                val values = enumList.elements.joinToString(", ") { "`${formatValueForDisplay(it)}`" }
                append("*Allowed values:* $values\n\n")
            }

            // Pattern constraint
            (props["pattern"] as? KsonString)?.value?.let {
                append("*Pattern:* `$it`\n\n")
            }

            // Numeric constraints
            (props["minimum"] as? KsonNumber)?.let {
                append("*Minimum:* ${it.value.asString}\n\n")
            }
            (props["maximum"] as? KsonNumber)?.let {
                append("*Maximum:* ${it.value.asString}\n\n")
            }

            // String length constraints
            (props["minLength"] as? KsonNumber)?.let {
                append("*Min length:* ${it.value.asString}\n\n")
            }
            (props["maxLength"] as? KsonNumber)?.let {
                append("*Max length:* ${it.value.asString}\n\n")
            }

            // Array length constraints
            (props["minItems"] as? KsonNumber)?.let {
                append("*Min items:* ${it.value.asString}\n\n")
            }
            (props["maxItems"] as? KsonNumber)?.let {
                append("*Max items:* ${it.value.asString}\n\n")
            }
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * Format a KsonValue for display in hover info.
     * Converts values to a readable string representation.
     */
    private fun formatValueForDisplay(value: KsonValue): String {
        return when (value) {
            is KsonString -> value.value
            is KsonNumber -> value.value.asString
            is KsonBoolean -> value.value.toString()
            is KsonNull -> "null"
            is KsonList -> "[${value.elements.joinToString(", ") { formatValueForDisplay(it) }}]"
            is KsonObject -> "{...}" // Don't expand objects in hover
            is EmbedBlock -> "<embed>" // Show that it's an embed block
        }
    }

    /**
     * Get schema hover information for a node in a document.
     *
     * This is the main entry point for IDE hover features.
     *
     * @param documentRoot The root of the document being edited
     * @param documentNode The specific node the cursor is hovering over
     * @param schemaValue The schema for the document (as KsonValue)
     * @return Formatted hover text, or null if no schema info available
     */
    fun getSchemaHoverInfo(
        documentRoot: KsonValue,
        documentNode: KsonValue,
        schemaValue: KsonValue
    ): String? {
        // Step 1: Build path from document root to target node
        val documentPath = KsonValueNavigation.buildPathTokens(documentRoot, documentNode)
            ?: return null

        // Step 2: Navigate schema to corresponding location
        val resolvedSchemaRef = navigateSchemaByDocumentPath(schemaValue, documentPath)
            ?: return null

        // Step 3: Extract and format hover information
        return extractSchemaHoverInfo(resolvedSchemaRef.resolvedValue)
    }
}
