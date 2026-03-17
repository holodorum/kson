package org.kson.jetbrains.parser

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.kson.DiagnosticMessage
import org.kson.DiagnosticSeverity
import org.kson.KsonTooling
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.jetbrains.schema.KsonSchemaService

class KsonValidationAnnotator : ExternalAnnotator<ValidationInfo?, List<DiagnosticMessage>>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): ValidationInfo? {
        if (file !is KsonPsiFile) return null

        val text = file.text
        if (text.isBlank()) return null

        val virtualFile = file.virtualFile
        val schemaText = if (virtualFile != null) {
            file.project.getService(KsonSchemaService::class.java)
                ?.getSchemaForFile(virtualFile)?.schemaText
        } else {
            null
        }

        return ValidationInfo(text, schemaText)
    }

    override fun doAnnotate(info: ValidationInfo?): List<DiagnosticMessage> {
        if (info == null) return emptyList()
        return KsonTooling.validateDocument(info.sourceText, info.schemaText)
    }

    override fun apply(file: PsiFile, annotationResult: List<DiagnosticMessage>?, holder: AnnotationHolder) {
        if (file !is KsonPsiFile || annotationResult == null) return

        val documentLength = file.textLength
        /**
         * Only annotate messages that were NOT already annotated during the parsing phase.
         * Core parse errors are annotated by the parser itself, so we skip them here to avoid duplication.
         * All other messages (warnings, validation errors) are annotated here.
         */
        annotationResult.forEach {
            val startOffset = it.range.startOffset.coerceAtLeast(0)
            val endOffset = it.range.endOffset.coerceAtMost(documentLength)

            if (startOffset >= endOffset || startOffset >= documentLength) return@forEach

            val severity = when (it.severity) {
                DiagnosticSeverity.ERROR -> HighlightSeverity.ERROR
                DiagnosticSeverity.WARNING -> HighlightSeverity.WARNING
                DiagnosticSeverity.COREPARSE_ERROR -> return@forEach
            }
            holder.newAnnotation(severity, it.message)
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
