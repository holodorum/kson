package org.kson.jetbrains.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import org.kson.jetbrains.file.KsonFileType

/**
 * Tells IntelliJ that KSON files are eligible for JSON Schema features
 * (schema mappings UI, status bar widget, validation, etc.).
 *
 * Only loaded when the JSON plugin is present, via `kson-withJson.xml`.
 */
class KsonJsonSchemaEnabler : JsonSchemaEnabler {

    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        return file.fileType == KsonFileType
    }

    override fun shouldShowSwitcherWidget(file: VirtualFile): Boolean {
        return file.fileType == KsonFileType
    }

    override fun canBeSchemaFile(file: VirtualFile): Boolean {
        return file.fileType == KsonFileType
    }
}
