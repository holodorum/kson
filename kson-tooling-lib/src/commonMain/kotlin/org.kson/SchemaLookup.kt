@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import org.kson.schema.SchemaRegistry
import org.kson.schema.ExtensionSchema

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
        val allSchemas = SchemaRegistry.getAllSchemas()

        for (schema in allSchemas) {
            for (ext in schema.fileExtensions) {
                if (fileName.endsWith(ext) || fileName.endsWith("$ext.kson")) {
                    return schema
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
}