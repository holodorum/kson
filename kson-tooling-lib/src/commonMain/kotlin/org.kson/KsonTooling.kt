@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.value.KsonValue as InternalKsonValue
import org.kson.value.KsonObject as InternalKsonObject
import org.kson.value.KsonList as InternalKsonList
import org.kson.value.KsonString as InternalKsonString
import org.kson.value.KsonNumber as InternalKsonNumber
import org.kson.value.EmbedBlock as InternalKsonEmbedBlock
import org.kson.value.KsonBoolean as InternalKsonBoolean
import org.kson.value.KsonNull as InternalKsonNull
import org.kson.value.EmbedBlock as InternalEmbedBlock
import org.kson.value.KsonObjectProperty as InternalKsonObjectProperty
import org.kson.value.KsonValueNavigation
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.parser.Location
import org.kson.parser.Coordinates
import org.kson.parser.NumberParser
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
     * Navigate a schema (as KsonValue) to find the sub-schema corresponding to a document path.
     *
     * This function translates document paths to schema paths by inserting schema-specific wrappers:
     * - For object properties: navigates through "properties" wrapper
     * - For array indices: navigates to "items" schema (all array elements share the same schema)
     * - Falls back to "additionalProperties" or "patternProperties" when specific property not found
     * - Resolves `$ref` references to their target schemas
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
        schemaValue: String,
        documentPathTokens: List<String>,
        currentBaseUri: String = ""
    ): ResolvedRef? {
        val parsedSchema = Kson.analyze(schemaValue).ksonValue ?: return null

        // Create SchemaIdLookup for resolving $ref references
        val idLookup = SchemaIdLookup(parsedSchema.toInternal())

        if (documentPathTokens.isEmpty()) {
            return ResolvedRef(parsedSchema.toInternal(), currentBaseUri)
        }

        var node: InternalKsonValue? = parsedSchema.toInternal()
        var updatedBaseUri = currentBaseUri

        for (token in documentPathTokens) {
            if (node !is InternalKsonObject) {
                return null
            }

            // Track $id changes for proper URI resolution
            node.propertyLookup["\$id"]?.let { idValue ->
                if (idValue is InternalKsonString) {
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

            // Resolve $ref if present
            if (node is InternalKsonObject) {
                val refValue = node.propertyLookup["\$ref"] as? InternalKsonString
                if (refValue != null) {
                    val resolved = idLookup.resolveRef(refValue.value, updatedBaseUri)
                    if (resolved != null) {
                        node = resolved.resolvedValue
                        updatedBaseUri = resolved.resolvedValueBaseUri
                    }
                }
            }
        }

        return node?.let { ResolvedRef(it, updatedBaseUri) }
    }

    /**
     * Navigate to the schema for array items.
     *
     * Looks for "items" or "additionalItems" schema properties.
     */
    private fun navigateArrayItems(schemaNode: InternalKsonObject): InternalKsonValue? {
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
    private fun navigateObjectProperty(schemaNode: InternalKsonObject, propertyName: String): InternalKsonValue? {
        // Try direct property lookup in "properties"
        val properties = schemaNode.propertyLookup["properties"] as? InternalKsonObject
        properties?.propertyLookup?.get(propertyName)?.let { return it }

        // Try pattern properties - check all patterns for a match
        val patternProperties = schemaNode.propertyLookup["patternProperties"] as? InternalKsonObject
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
    fun extractSchemaHoverInfo(schemaNode: InternalKsonValue): String? {
        if (schemaNode !is InternalKsonObject) return null

        val props = schemaNode.propertyLookup

        return buildString {
            // Title (bold header)
            (props["title"] as? InternalKsonString)?.value?.let {
                append("**$it**\n\n")
            }

            // Description (main documentation)
            (props["description"] as? InternalKsonString)?.value?.let {
                append("$it\n\n")
            }

            // Type information
            when (val typeValue = props["type"]) {
                is InternalKsonString -> {
                    append("*Type:* `${typeValue.value}`\n\n")
                }
                is InternalKsonList -> {
                    // Union type: ["string", "number"]
                    val types = typeValue.elements.mapNotNull { (it as? InternalKsonString)?.value }
                    if (types.isNotEmpty()) {
                        append("*Type:* `${types.joinToString(" | ")}`\n\n")
                    }
                }
                is InternalKsonBoolean, is InternalKsonNull, is InternalKsonNumber, is InternalKsonObject, is InternalEmbedBlock, null -> {
                    // These types are not expected for the "type" property in a schema
                    // We simply don't add type information in these cases
                }
            }

            // Default value
            props["default"]?.let {
                append("*Default:* `${formatValueForDisplay(it)}`\n\n")
            }

            // Enum values
            (props["enum"] as? InternalKsonList)?.let { enumList ->
                val values = enumList.elements.joinToString(", ") { "`${formatValueForDisplay(it)}`" }
                append("*Allowed values:* $values\n\n")
            }

            // Pattern constraint
            (props["pattern"] as? InternalKsonString)?.value?.let {
                append("*Pattern:* `$it`\n\n")
            }

            // Numeric constraints
            (props["minimum"] as? InternalKsonNumber)?.let {
                append("*Minimum:* ${it.value.asString}\n\n")
            }
            (props["maximum"] as? InternalKsonNumber)?.let {
                append("*Maximum:* ${it.value.asString}\n\n")
            }

            // String length constraints
            (props["minLength"] as? InternalKsonNumber)?.let {
                append("*Min length:* ${it.value.asString}\n\n")
            }
            (props["maxLength"] as? InternalKsonNumber)?.let {
                append("*Max length:* ${it.value.asString}\n\n")
            }

            // Array length constraints
            (props["minItems"] as? InternalKsonNumber)?.let {
                append("*Min items:* ${it.value.asString}\n\n")
            }
            (props["maxItems"] as? InternalKsonNumber)?.let {
                append("*Max items:* ${it.value.asString}\n\n")
            }
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * Format a KsonValue for display in hover info.
     * Converts values to a readable string representation.
     */
    private fun formatValueForDisplay(value: InternalKsonValue): String {
        return when (value) {
            is InternalKsonList -> "[${value.elements.joinToString(",") { formatValueForDisplay(it) }}]"
            is InternalKsonBoolean -> value.value.toString()
            is InternalKsonEmbedBlock -> "<embed>"
            is InternalKsonNull -> "null"
            is InternalKsonNumber -> value.value.asString
            is InternalKsonObject -> "{...}"
            is InternalKsonString -> value.value
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
        documentRoot: InternalKsonValue,
        documentNode: InternalKsonValue,
        schemaValue: String
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
        return getSchemaHoverInfo(documentRootValue, targetNode, schemaValue)
    }
}


/**
 * Convert a public [KsonValue] to an internal [InternalKsonValue].
 *
 * This allows the public API to accept public types while working with internal types internally.
 */
fun KsonValue.toInternal(): InternalKsonValue {
    // Create a dummy location since public types don't have full location information
    val location = Location(
        start = Coordinates(this.start.line, this.start.column),
        end = Coordinates(this.end.line, this.end.column),
        startOffset = 0,
        endOffset = 0
    )

    return when (this) {
        is KsonValue.KsonObject -> {
            InternalKsonObject(
                propertyMap = this.properties.mapKeys { (key, _) -> key.value }.mapValues { (key, value) ->
                    val keyString = this.properties.keys.first { it.value == key }
                    InternalKsonObjectProperty(
                        propName = keyString.toInternal() as InternalKsonString,
                        propValue = value.toInternal()
                    )
                },
                location = location
            )
        }
        is KsonValue.KsonArray -> {
            InternalKsonList(
                elements = this.elements.map { it.toInternal() },
                location = location
            )
        }
        is KsonValue.KsonString -> {
            InternalKsonString(
                value = this.value,
                location = location
            )
        }
        is KsonValue.KsonNumber.Integer -> {
            InternalKsonNumber(
                value = NumberParser.ParsedNumber.Integer(this.value.toString()),
                location = location
            )
        }
        is KsonValue.KsonNumber.Decimal -> {
            InternalKsonNumber(
                value = NumberParser.ParsedNumber.Decimal(this.value.toString()),
                location = location
            )
        }
        is KsonValue.KsonBoolean -> {
            InternalKsonBoolean(
                value = this.value,
                location = location
            )
        }
        is KsonValue.KsonNull -> {
            InternalKsonNull(location = location)
        }
        is KsonValue.KsonEmbed -> {
            val tagString = this.tag?.let {
                InternalKsonString(it, location)
            }
            val metadataString = this.metadata?.let {
                InternalKsonString(it, location)
            }
            InternalEmbedBlock(
                embedTag = tagString,
                metadataTag = metadataString,
                embedContent = InternalKsonString(this.content, location),
                location = location
            )
        }
    }
}