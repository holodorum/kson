package org.kson.navigation

import org.kson.schema.SchemaIdLookup
import org.kson.value.KsonValue as InternalKsonValue
import org.kson.value.KsonObject as InternalKsonObject
import org.kson.value.KsonList as InternalKsonList
import org.kson.value.KsonString as InternalKsonString
import org.kson.value.KsonNumber as InternalKsonNumber
import org.kson.value.EmbedBlock as InternalEmbedBlock
import org.kson.value.KsonBoolean as InternalKsonBoolean
import org.kson.value.KsonNull as InternalKsonNull
import org.kson.value.KsonValueNavigation

internal object SchemaNavigation{
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
    fun getSchemaInfo(
        documentRoot: InternalKsonValue,
        documentNode: InternalKsonValue,
        schemaValue: InternalKsonValue
    ): String? {
        // Step 1: Build path from document root to target node
        val documentPath = KsonValueNavigation.buildPathTokens(documentRoot, documentNode)
            ?: return null

        // Step 2: Navigate schema to corresponding location
        val resolvedSchemaRef = SchemaIdLookup(schemaValue).navigateByDocumentPath(
            documentPathTokens = documentPath
        )

        // Step 3: Extract and format hover information
        return resolvedSchemaRef?.resolvedValue?.extractSchemaInfo()
    }
}

/**
 * Extract schema information from a schema node.
 *
 * Formats schema metadata into markdown suitable for IDE hover tooltips.
 * Includes: title, description, type, default value, constraints (enum, pattern, min/max).
 *
 * @return Formatted markdown string, or null if no hover info available
 */
fun InternalKsonValue.extractSchemaInfo(): String? {
    if (this !is InternalKsonObject) return null

    val props: Map<String, InternalKsonValue> = this.propertyLookup

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
        when (val typeValue: InternalKsonValue? = props["type"]) {
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
            append("*Default:* `${it.formatValueForDisplay()}`\n\n")
        }

        // Enum values
        (props["enum"] as? InternalKsonList)?.let { enumList ->
            val values = enumList.elements.joinToString(", ") { "`${it.formatValueForDisplay()}`" }
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
 * Format a KsonValue for display in info.
 * Converts values to a readable string representation.
 */
fun InternalKsonValue.formatValueForDisplay(): String {
    return when (this) {
        is InternalKsonList -> "[${this.elements.joinToString(",") { it.formatValueForDisplay() }}]"
        is InternalKsonBoolean -> this.value.toString()
        is InternalEmbedBlock -> "<embed>"
        is InternalKsonNull -> "null"
        is InternalKsonNumber -> this.value.asString
        is InternalKsonObject -> "{...}"
        is InternalKsonString -> this.value
    }
}