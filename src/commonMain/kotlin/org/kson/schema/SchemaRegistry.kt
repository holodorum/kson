@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson.schema

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Global registry for extension-provided schemas.
 *
 * This is the base registry that allows extensions (like KsonHub) to register schemas.
 * It provides only registration and storage functionality. For query functionality
 * (like finding schemas for files), use the extended API in kson-tooling-lib.
 *
 * Extensions register schemas using [registerExtension] and [unregisterExtension].
 */
object SchemaRegistry {

    private val extensionSchemas = mutableMapOf<String, List<ExtensionSchema>>()

    /**
     * Register schemas for an extension.
     *
     * If the extension is already registered, its schemas will be replaced.
     *
     * @param extensionId Unique identifier for the extension (e.g., "ksonhub.my-extension")
     * @param schemas List of schemas to register for this extension
     */
    fun registerExtension(extensionId: String, schemas: List<ExtensionSchema>) {
        extensionSchemas[extensionId] = schemas
    }

    /**
     * Unregister all schemas for an extension.
     *
     * @param extensionId The extension identifier to unregister
     */
    fun unregisterExtension(extensionId: String) {
        extensionSchemas.remove(extensionId)
    }

    /**
     * Get all registered schemas from all extensions.
     *
     * @return List of all registered ExtensionSchema objects
     */
    fun getAllSchemas(): List<ExtensionSchema> {
        return extensionSchemas.values.flatten()
    }

    /**
     * Clear all registered schemas. Primarily for testing.
     */
    fun clear() {
        extensionSchemas.clear()
    }
}

/**
 * Represents a schema registered by an extension.
 *
 * @param schemaUri URI identifier for the schema (e.g., "ksonhub://schemas/my-schema")
 * @param schema The parsed JsonSchema (raw source accessible via [JsonSchema.rawSchema])
 * @param fileExtensions File extensions this schema applies to (e.g., ".myext", ".foo")
 */
data class ExtensionSchema(
    val schemaUri: String,
    val schema: JsonSchema,
    val fileExtensions: List<String>
)