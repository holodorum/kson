package org.kson.jetbrains.schema.walker

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.kson.jetbrains.psi.KsonPsiFile

/**
 * Registers KSON as a language that IntelliJ's JSON Schema infrastructure can walk.
 *
 * When IntelliJ needs to read a schema file or validate a document, it queries all
 * [JsonLikePsiWalkerFactory] extensions to find one that handles the file's PSI elements.
 * This factory returns [KsonJsonLikePsiWalker] for KSON files, enabling .kson files to be
 * used as schema sources in the JSON Schema Mappings UI.
 */
class KsonJsonLikePsiWalkerFactory : JsonLikePsiWalkerFactory {

    override fun handles(element: PsiElement): Boolean {
        return element.containingFile is KsonPsiFile
    }

    override fun create(schemaObject: JsonSchemaObject): JsonLikePsiWalker {
        return KsonJsonLikePsiWalker
    }
}
