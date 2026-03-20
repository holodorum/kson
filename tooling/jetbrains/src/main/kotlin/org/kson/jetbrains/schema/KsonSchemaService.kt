package org.kson.jetbrains.schema

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.kson.KsonCore
import org.kson.schema.KsonDraft7MetaSchema
import org.kson.value.KsonObject
import org.kson.value.KsonString
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The result of resolving a schema for a KSON file.
 *
 * @param schemaText The schema source text
 * @param schemaFile The virtual file the schema was loaded from, or null for bundled schemas
 */
data class SchemaResolution(val schemaText: String, val schemaFile: VirtualFile?)

/**
 * Project-level service that resolves which JSON schema applies to a given KSON file.
 *
 * Resolution order:
 * 1. `$schema` property in the document root
 * 2. Any registered [KsonSchemaResolver] (e.g. IntelliJ's JSON Schema Mappings UI)
 * 3. Bundled metaschema for `.schema.kson` files
 */
@Service(Service.Level.PROJECT)
class KsonSchemaService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): KsonSchemaService = project.service()

        private const val SCHEMA_KSON_SUFFIX = ".schema.kson"
    }

    private val resolvers = CopyOnWriteArrayList<KsonSchemaResolver>()

    fun registerResolver(resolver: KsonSchemaResolver) {
        resolvers.add(resolver)
    }

    fun getSchemaForFile(file: VirtualFile): SchemaResolution? {
        return resolveFromDollarSchema(file)
            ?: resolveFromRegisteredResolvers(file)
            ?: resolveFromBundledMetaschema(file)
    }

    /**
     * Check for a `$schema` property at the root of the document.
     *
     * Quick-parses the file and looks for a `$schema` key whose value is a relative
     * path to a schema file. The path is resolved relative to the file's parent directory.
     */
    internal fun resolveFromDollarSchema(file: VirtualFile): SchemaResolution? {
        val sourceText = try {
            String(file.contentsToByteArray(), Charsets.UTF_8)
        } catch (_: IOException) {
            return null
        }

        val schemaPath = extractDollarSchemaPath(sourceText) ?: return null
        val parentDir = file.parent ?: return null
        val schemaFile = parentDir.findFileByRelativePath(schemaPath) ?: return null

        val schemaText = try {
            String(schemaFile.contentsToByteArray(), Charsets.UTF_8)
        } catch (_: IOException) {
            return null
        }

        return SchemaResolution(schemaText, schemaFile)
    }

    private fun resolveFromRegisteredResolvers(file: VirtualFile): SchemaResolution? {
        for (resolver in resolvers) {
            val resolution = resolver.getSchemaForFile(project, file)
            if (resolution != null) return resolution
        }
        return null
    }

    /**
     * For `.schema.kson` files, apply the bundled Draft 7 metaschema so that
     * schema authors get validation of their schema declarations.
     */
    private fun resolveFromBundledMetaschema(file: VirtualFile): SchemaResolution? {
        if (!file.name.endsWith(SCHEMA_KSON_SUFFIX)) return null
        return SchemaResolution(KsonDraft7MetaSchema.SOURCE, null)
    }
}

/**
 * Extract the `$schema` path from a KSON document's root.
 *
 * Parses the document and looks for a root-level `$schema` property with a string value.
 * Returns the string value (expected to be a relative path), or null if the document has
 * parse errors or no `$schema` property is found.
 */
internal fun extractDollarSchemaPath(sourceText: String): String? {
    val result = KsonCore.parseToAst(sourceText)
    val ksonValue = result.ksonValue ?: return null

    if (ksonValue !is KsonObject) return null
    val schemaProp = ksonValue.propertyMap["\$schema"] ?: return null
    val schemaValue = schemaProp.propValue
    if (schemaValue !is KsonString) return null

    return schemaValue.value
}
