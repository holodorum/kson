@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import org.kson.schema.JsonSchema
import org.kson.schema.ExtensionSchema as InternalExtensionSchema
import kotlin.js.ExperimentalJsExport
import org.kson.schema.SchemaRegistry
import org.kson.schema.TestJsonSchema
import kotlin.js.JsExport

/**
 * Schema lookup utilities for finding schemas that match files.
 *
 * This object provides query functionality on top of the base [SchemaRegistry].
 * Use [SchemaRegistry] for registration and this object for lookups.
 */
object SchemaLookup {

    /**
     * Get the schema for a given file URI.
     *
     * Matches by file extension (e.g., ".myext" matches "file.myext" or "file.myext.kson").
     *
     * @param fileUri The URI of the file (e.g., "file:///path/to/config.myext")
     * @return The matching ExtensionSchema, or null if no match found
     */
    fun getSchemaForFile(fileUri: String): ExtensionSchema? {
        val fileName = extractFileName(fileUri)
        val allSchemas: List<InternalExtensionSchema> = SchemaRegistry.getAllSchemas()

        for (internalSchema: InternalExtensionSchema in allSchemas) {
            for (ext in internalSchema.fileExtensions) {
                if (fileName.endsWith(ext) || fileName.endsWith("$ext.kson")) {
                    return ExtensionSchema(
                        schemaUri = internalSchema.schemaUri,
                        schema = internalSchema.schema,
                        fileExtensions = internalSchema.fileExtensions.toTypedArray()
                    )
                }
            }
        }

        return null
    }

    /**
     * Extract the file name from a URI.
     * Handles both file:// URIs and plain paths.
     */
    private fun extractFileName(fileUri: String): String {
        val path = extractFilePath(fileUri)
        val lastSlash = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        return if (lastSlash >= 0) path.substring(lastSlash + 1) else path
    }

    /**
     * Extract the file path from a URI, removing the scheme if present.
     */
    private fun extractFilePath(fileUri: String): String {
        return when {
            fileUri.startsWith("file:///") -> {
                // Handle Windows paths (file:///C:/...) and Unix paths (file:///path/...)
                val path = fileUri.removePrefix("file://")
                // On Windows, remove leading slash before drive letter
                if (path.length > 2 && path[0] == '/' && path[2] == ':') {
                    path.substring(1)
                } else {
                    path
                }
            }
            fileUri.startsWith("file://") -> fileUri.removePrefix("file://")
            else -> fileUri
        }
    }

    // ===================================================================================
    // Test utilities
    //
    // These methods exist in production code because TypeScript tests in the
    // language-server-protocol module need a JS-callable API for test fixtures.
    // SchemaRegistry.registerExtension() requires ExtensionSchema objects with JsonSchema
    // instances, which aren't easily constructable from JavaScript. These methods provide
    // a primitive-based API that bridges the Kotlin/JS boundary for tests.
    //
    // Do not use in production code.
    // ===================================================================================

    /**
     * @suppress Not for production use. Test utility for registering schema fixtures.
     */
    fun _registerForTesting(
        extensionId: String,
        schemaUri: String,
        rawSchemaContent: String,
        fileExtensions: Array<String>
    ) {
        SchemaRegistry.registerExtension(
            extensionId,
            listOf(
                InternalExtensionSchema(
                    schemaUri = schemaUri,
                    schema = TestJsonSchema(rawSchemaContent),
                    fileExtensions = fileExtensions.toList()
                )
            )
        )
    }

    /**
     * @suppress Not for production use. Test utility for clearing registered schemas.
     */
    fun _clearForTesting() {
        SchemaRegistry.clear()
    }
}

/**
 * Represents a schema registered by an extension.
 *
 * @param schemaUri URI identifier for the schema (e.g., "ksonhub://schemas/my-schema")
 * @param schema The parsed JsonSchema (raw source accessible via [JsonSchema.rawSchema])
 * @param fileExtensions File extensions this schema applies to (e.g., ".myext", ".foo")
 */
class ExtensionSchema(
    val schemaUri: String,
    val schema: JsonSchema,
    val fileExtensions: Array<String>
){
    fun getRawSchema(): String? {
        return this.schema.rawSchema
    }
}