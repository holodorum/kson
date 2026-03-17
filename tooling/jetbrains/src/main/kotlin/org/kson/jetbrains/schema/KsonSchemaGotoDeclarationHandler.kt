package org.kson.jetbrains.schema

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.kson.KsonTooling
import org.kson.ToolingDocument
import org.kson.jetbrains.psi.KsonPsiFile
import java.io.IOException

/**
 * Enables "Go to Declaration" from a property key in a KSON document to its definition
 * in the associated schema.
 *
 * Delegates to [KsonTooling.getSchemaLocationAtLocation] which handles KSON schema
 * parsing, `$ref` resolution, and combinator filtering.
 */
class KsonSchemaGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        element: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (element == null) return null
        val file = element.containingFile as? KsonPsiFile ?: return null
        val virtualFile = file.virtualFile ?: return null

        val resolution = file.project.getService(KsonSchemaService::class.java)
            ?.getSchemaForFile(virtualFile) ?: return null

        val document = KsonTooling.parse(file.text)
        val position = editor?.caretModel?.offset ?: offset
        val lineCol = lineColumnFromOffset(file.text, position) ?: return null

        val ranges = KsonTooling.getSchemaLocationAtLocation(
            document, resolution.schemaText, lineCol.first, lineCol.second
        )
        if (ranges.isEmpty()) return null

        // Resolve the schema file to navigate to
        val schemaVirtualFile = resolution.schemaFile ?: return null
        val schemaPsiFile = PsiManager.getInstance(file.project).findFile(schemaVirtualFile) ?: return null
        val schemaText = try {
            String(schemaVirtualFile.contentsToByteArray(), Charsets.UTF_8)
        } catch (_: IOException) {
            return null
        }

        val targets = ranges.mapNotNull { range ->
            val targetOffset = offsetFromLineColumn(schemaText, range.startLine, range.startColumn) ?: return@mapNotNull null
            schemaPsiFile.findElementAt(targetOffset)
        }

        return if (targets.isNotEmpty()) targets.toTypedArray() else null
    }
}
