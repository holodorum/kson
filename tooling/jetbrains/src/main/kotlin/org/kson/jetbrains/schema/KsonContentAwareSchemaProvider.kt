package org.kson.jetbrains.schema

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.extension.ContentAwareJsonSchemaFileProvider
import org.kson.Json
import org.kson.KsonCore
import org.kson.jetbrains.file.KsonFileType

/**
 * Per-file schema resolver for IntelliJ's JSON Schema widget.
 *
 * Delegates to [KsonSchemaService] (which handles `$schema`, registered resolvers,
 * and `.schema.kson` metaschema detection) and converts the resolved KSON schema
 * to JSON so IntelliJ can consume it.
 *
 * This complements [KsonJsonSchemaProviderFactory] by handling the dynamic cases
 * (`$schema` property, user-configured mappings) that can't be expressed as static
 * [JsonSchemaFileProvider] instances.
 */
class KsonContentAwareSchemaProvider : ContentAwareJsonSchemaFileProvider {

    override fun getSchemaFile(psiFile: PsiFile): VirtualFile? {
        if (psiFile.fileType != KsonFileType) return null
        val virtualFile = psiFile.virtualFile ?: return null

        // Skip .schema.kson files — those are handled by KsonMetaSchemaProvider
        if (virtualFile.name.endsWith(".schema.kson")) return null

        val project = psiFile.project
        val resolution = KsonSchemaService.getInstance(project)
            .getSchemaForFile(virtualFile) ?: return null

        val json = KsonCore.parseToJson(
            resolution.schemaText,
            Json(retainEmbedTags = false)
        ).json ?: return null

        val baseName = resolution.schemaFile?.nameWithoutExtension ?: "kson-schema"
        return LightVirtualFile("$baseName.json", json)
    }
}
