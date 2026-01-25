@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package org.kson

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Global registry for extension-provided schemas.
 *
 * This registry allows extensions (like KsonHub) to register schemas that should
 * be automatically applied to documents based on file extensions or glob patterns.
 * The Language Server queries this registry to provide schema-aware features
 * (validation, completions, hover) for documents that match registered patterns.
 *
 * Extensions can register schemas using [registerExtension] and the Language Server
 * can query schemas using [getSchemaForFile].
 */
object SchemaRegistry {

    private val extensionSchemas = mutableMapOf<String, Array<ExtensionSchema>>()
    private var changeListener: ((String) -> Unit)? = null
    private var version: Int = 0

    /**
     * Register schemas for an extension.
     *
     * If the extension is already registered, its schemas will be replaced.
     *
     * @param extensionId Unique identifier for the extension (e.g., "ksonhub.my-extension")
     * @param schemas Array of schemas to register for this extension
     */
    fun registerExtension(extensionId: String, schemas: Array<ExtensionSchema>) {
        extensionSchemas[extensionId] = schemas
        version++
        changeListener?.invoke(extensionId)
    }

    /**
     * Unregister all schemas for an extension.
     *
     * @param extensionId The extension identifier to unregister
     */
    fun unregisterExtension(extensionId: String) {
        if (extensionSchemas.remove(extensionId) != null) {
            version++
            changeListener?.invoke(extensionId)
        }
    }

    /**
     * Get the schema for a given file URI.
     *
     * Resolution priority:
     * 1. File extension match (e.g., ".myext" matches "file.myext.kson")
     * 2. Glob pattern match (e.g., glob patterns like "config/app.kson")
     *
     * @param fileUri The URI of the file (e.g., "file:///path/to/config.myext")
     * @return The matching ExtensionSchema, or null if no match found
     */
    fun getSchemaForFile(fileUri: String): ExtensionSchema? {
        val fileName = extractFileName(fileUri)

        // First, try file extension matching (higher priority)
        for (schemas in extensionSchemas.values) {
            for (schema in schemas) {
                for (ext in schema.fileExtensions) {
                    if (fileName.endsWith(ext) || fileName.endsWith("$ext.kson")) {
                        return schema
                    }
                }
            }
        }

        // Then, try glob pattern matching (lower priority)
        val filePath = extractFilePath(fileUri)
        for (schemas in extensionSchemas.values) {
            for (schema in schemas) {
                for (pattern in schema.fileMatch) {
                    if (matchesGlobPattern(filePath, pattern)) {
                        return schema
                    }
                }
            }
        }

        return null
    }

    /**
     * Get all registered schemas from all extensions.
     *
     * @return Array of all registered ExtensionSchema objects
     */
    fun getAllSchemas(): Array<ExtensionSchema> {
        return extensionSchemas.values.flatMap { it.toList() }.toTypedArray()
    }

    /**
     * Get the current version number.
     *
     * The version is incremented each time schemas are registered or unregistered.
     * Can be used for polling-based change detection.
     *
     * @return The current version number
     */
    fun getVersion(): Int = version

    /**
     * Set a callback to be notified when schemas change.
     *
     * Only one listener can be registered at a time. Setting a new listener
     * replaces any previously registered listener.
     *
     * @param listener Callback that receives the extensionId that changed
     */
    fun setOnChangeListener(listener: ((String) -> Unit)?) {
        changeListener = listener
    }

    /**
     * Clear all registered schemas. Primarily for testing.
     */
    fun clear() {
        extensionSchemas.clear()
        version++
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

    /**
     * Simple glob pattern matching.
     * Supports * (match any characters except path separators) and ** (match any path).
     */
    private fun matchesGlobPattern(path: String, pattern: String): Boolean {
        // Normalize path separators
        val normalizedPath = path.replace('\\', '/')
        val normalizedPattern = pattern.replace('\\', '/')

        // Convert glob pattern to regex
        val regexPattern = buildString {
            var i = 0
            while (i < normalizedPattern.length) {
                when {
                    i + 1 < normalizedPattern.length &&
                    normalizedPattern[i] == '*' &&
                    normalizedPattern[i + 1] == '*' -> {
                        append(".*")
                        i += 2
                        // Skip trailing slash after **
                        if (i < normalizedPattern.length && normalizedPattern[i] == '/') {
                            i++
                        }
                    }
                    normalizedPattern[i] == '*' -> {
                        append("[^/]*")
                        i++
                    }
                    normalizedPattern[i] == '?' -> {
                        append("[^/]")
                        i++
                    }
                    normalizedPattern[i] in "[]().+^\${}|" -> {
                        append('\\')
                        append(normalizedPattern[i])
                        i++
                    }
                    else -> {
                        append(normalizedPattern[i])
                        i++
                    }
                }
            }
        }

        return Regex("^$regexPattern$").matches(normalizedPath) ||
               Regex(".*/$regexPattern$").matches(normalizedPath)
    }
}

/**
 * Represents a schema registered by an extension.
 *
 * @param schemaUri URI identifier for the schema (e.g., "ksonhub://schemas/my-schema")
 * @param schemaContent The KSON schema content as a string
 * @param fileExtensions File extensions this schema applies to (e.g., ".myext", ".foo")
 * @param fileMatch Glob patterns this schema applies to (e.g., "config/app.kson")
 */
class ExtensionSchema(
    val schemaUri: String,
    val schemaContent: String,
    val fileExtensions: Array<String>,
    val fileMatch: Array<String>
)
