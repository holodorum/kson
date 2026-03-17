package org.kson.jetbrains.schema

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.kson.CompletionKind
import org.kson.KsonTooling
import org.kson.jetbrains.psi.KsonPsiFile

/**
 * Provides schema-driven completions for KSON files.
 *
 * Delegates to [KsonTooling.getCompletionsAtLocation] which handles KSON schema parsing,
 * combinator expansion/filtering, and completion item generation.
 */
class KsonSchemaCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile as? KsonPsiFile ?: return
        val virtualFile = file.virtualFile ?: return

        val resolution = file.project.getService(KsonSchemaService::class.java)
            ?.getSchemaForFile(virtualFile) ?: return

        val document = KsonTooling.parse(file.text)
        val offset = parameters.offset
        val lineCol = lineColumnFromOffset(file.text, offset) ?: return

        val completions = KsonTooling.getCompletionsAtLocation(
            document, resolution.schemaText, lineCol.first, lineCol.second
        )

        for (item in completions) {
            var builder = LookupElementBuilder.create(item.label)
            if (item.detail != null) {
                builder = builder.withTypeText(item.detail)
            }
            if (item.kind == CompletionKind.PROPERTY) {
                // For property completions, append ": " after inserting
                builder = builder.withInsertHandler { context, _ ->
                    val doc = context.document
                    val tailOffset = context.tailOffset
                    // Only add ": " if not already followed by ":"
                    val existingText = doc.getText(com.intellij.openapi.util.TextRange(tailOffset, minOf(tailOffset + 2, doc.textLength)))
                    if (!existingText.startsWith(":")) {
                        doc.insertString(tailOffset, ": ")
                        context.editor.caretModel.moveToOffset(tailOffset + 2)
                    }
                }
            }
            result.addElement(builder)
        }
    }
}
