package org.kson.jetbrains.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import java.io.IOException

/**
 * Resolver that queries IntelliJ's [JsonSchemaService] for schema mappings.
 *
 * This bridges IntelliJ's JSON Schema Mappings UI (where users can manually associate
 * schemas with file patterns) to KSON's schema resolution. Only loaded when the JSON
 * plugin is available, via [KsonJsonSchemaRegistrar].
 */
class KsonJsonSchemaResolver : KsonSchemaResolver {

    override fun getSchemaForFile(project: Project, file: VirtualFile): SchemaResolution? {
        val jsonSchemaService = JsonSchemaService.Impl.get(project)
        val schemaFiles = jsonSchemaService.getSchemaFilesForFile(file)

        if (schemaFiles.isEmpty()) return null

        val schemaFile = schemaFiles.first()
        val schemaText = try {
            String(schemaFile.contentsToByteArray(), Charsets.UTF_8)
        } catch (_: IOException) {
            return null
        }

        return SchemaResolution(schemaText, schemaFile)
    }
}
