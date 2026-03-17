package org.kson.jetbrains.parser

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.jetbrains.schema.KsonSchemaService
import org.kson.parser.LoggedMessage
import org.kson.parser.messages.Message
import org.kson.parser.messages.MessageSeverity

class KsonValidationAnnotator : ExternalAnnotator<ValidationInfo?, List<LoggedMessage>>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): ValidationInfo? {
        if (file !is KsonPsiFile) return null

        val text = file.text
        if (text.isBlank()) return null

        val virtualFile = file.virtualFile
        val schemaText = if (virtualFile != null) {
            file.project.getServiceIfCreated(KsonSchemaService::class.java)
                ?.getSchemaForFile(virtualFile)?.schemaText
        } else {
            null
        }

        return ValidationInfo(text, schemaText)
    }

    override fun doAnnotate(info: ValidationInfo?): List<LoggedMessage> {
        if (info == null) return emptyList()

        val schema = info.schemaText?.let { KsonCore.parseSchema(it).jsonSchema }
        val config = if (schema != null) CoreCompileConfig(schemaJson = schema) else CoreCompileConfig()

        return KsonCore.parseToAst(info.sourceText, config).messages
    }

    override fun apply(file: PsiFile, annotationResult: List<LoggedMessage>?, holder: AnnotationHolder) {
        if (file !is KsonPsiFile || annotationResult == null) return

        val documentLength = file.textLength
        /**
         * Only annotate messages that were NOT already annotated during the parsing phase.
         * Core parse errors are annotated by the parser itself, so we skip them here to avoid duplication.
         * All other messages (warnings, validation errors) are annotated here.
         */
        annotationResult.filter { !Message.isFatalParseError(it.message) }
            .forEach {
                val startOffset = it.location.startOffset.coerceAtLeast(0)
                val endOffset = it.location.endOffset.coerceAtMost(documentLength)

                if (startOffset >= endOffset || startOffset >= documentLength) return@forEach

                val severity = when (it.message.type.severity) {
                    MessageSeverity.ERROR -> HighlightSeverity.ERROR
                    MessageSeverity.WARNING -> HighlightSeverity.WARNING
                }
                holder.newAnnotation(severity, it.message.toString())
                    .range(TextRange(startOffset, endOffset))
                    .create()
            }
    }
}

/**
 * Carries information between the [ExternalAnnotator] phases.
 *
 * @param sourceText The document source text to validate
 * @param schemaText The resolved schema source text, or null if no schema applies
 */
data class ValidationInfo(
    val sourceText: String,
    val schemaText: String? = null
)
