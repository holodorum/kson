package org.kson.jetbrains.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.kson.Json
import org.kson.KsonCore
import org.kson.jetbrains.file.KsonFileType
import org.kson.schema.KsonDraft7MetaSchema

/**
 * Provides JSON Schema providers for KSON files to IntelliJ's schema infrastructure.
 *
 * Two providers are created:
 * - [KsonMetaSchemaProvider]: applies the bundled Draft-07 metaschema to `.schema.kson` files
 * - [KsonDollarSchemaProvider]: resolves `$schema` references in KSON documents
 *
 * These make schemas appear in IntelliJ's status bar widget. Schema content is converted
 * from KSON to JSON so that IntelliJ can consume it natively.
 */
class KsonJsonSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return listOf(
            KsonMetaSchemaProvider(),
            KsonDollarSchemaProvider(project)
        )
    }
}

/**
 * Provides the KSON Draft-07 metaschema for `.schema.kson` files.
 *
 * The metaschema is lazily converted from KSON to JSON and served as a [LightVirtualFile]
 * so that IntelliJ's schema infrastructure can parse it.
 */
private class KsonMetaSchemaProvider : JsonSchemaFileProvider {

    private val metaSchemaJsonFile: VirtualFile by lazy {
        val json = KsonCore.parseToJson(
            KsonDraft7MetaSchema.SOURCE,
            Json(retainEmbedTags = false)
        ).json ?: error("Failed to convert KSON Draft-07 metaschema to JSON")
        LightVirtualFile("kson-draft-07-metaschema.json", json)
    }

    override fun isAvailable(file: VirtualFile): Boolean {
        return file.fileType == KsonFileType && file.name.endsWith(".schema.kson")
    }

    override fun getName(): String = "KSON Draft-07 Metaschema"

    override fun getSchemaFile(): VirtualFile = metaSchemaJsonFile

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}

/**
 * Resolves `$schema` references in KSON documents for IntelliJ's status bar widget.
 *
 * IntelliJ's widget only consults [JsonSchemaProviderFactory] providers (not
 * [ContentAwareJsonSchemaFileProvider]), so this provider bridges KSON's `$schema`
 * resolution into that path. Delegates to [KsonSchemaService.resolveFromDollarSchema]
 * for the actual resolution to avoid duplicating that logic.
 *
 * IntelliJ builds a provider map at initialization by calling [getSchemaFile] on each
 * provider. Providers with null schema files are excluded from the map and never
 * consulted. To work around this, we use a stable [LightVirtualFile] whose content
 * is updated by [isAvailable] when a `$schema` reference is resolved. This is safe
 * because IntelliJ calls [isAvailable] and [getSchemaFile] sequentially per-file
 * on the EDT, but it means this provider can only correctly serve one file at a time.
 */
private class KsonDollarSchemaProvider(private val project: Project) : JsonSchemaFileProvider {

    private val schemaJsonFile = LightVirtualFile("kson-dollar-schema.json", "{}")

    override fun isAvailable(file: VirtualFile): Boolean {
        if (file.fileType != KsonFileType) return false
        if (file.name.endsWith(".schema.kson")) return false

        val resolution = KsonSchemaService.getInstance(project)
            .resolveFromDollarSchema(file) ?: return false

        val json = KsonCore.parseToJson(resolution.schemaText, Json(retainEmbedTags = false)).json
            ?: return false

        schemaJsonFile.setContent(this, json, true)
        return true
    }

    override fun getName(): String = "KSON Schema"

    override fun getSchemaFile(): VirtualFile = schemaJsonFile

    override fun getSchemaType(): SchemaType = SchemaType.schema
}
