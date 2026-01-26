package org.kson.schema

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
    private var changeListener: ((String) -> Unit)? = null

    /**
     * Set a listener that will be called when schemas are registered or unregistered.
     *
     * @param listener Callback that receives the extensionId that changed, or null to remove the listener
     */
    fun setOnChangeListener(listener: ((String) -> Unit)?) {
        changeListener = listener
    }

    /**
     * Register schemas for an extension.
     *
     * If the extension is already registered, its schemas will be replaced.
     *
     * @param extensionId Unique identifier for the extension (e.g., "ksonhub.my-extension")
     * @param schemas List of schemas to register for this extension
     */
    fun registerExtension(extensionId: String, schemas: List<ExtensionSchema>) {
        extensionSchemas[extensionId] = schemas.toList()
        changeListener?.invoke(extensionId)
    }

    /**
     * Unregister all schemas for an extension.
     *
     * @param extensionId The extension identifier to unregister
     */
    fun unregisterExtension(extensionId: String) {
        extensionSchemas.remove(extensionId)
        changeListener?.invoke(extensionId)
    }

    /**
     * Get all registered schemas from all extensions.
     *
     * @return List of all registered ExtensionSchema objects
     */
    fun getAllSchemas(): Array<ExtensionSchema> {
        return extensionSchemas.values.flatten().toTypedArray()
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