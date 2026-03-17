package org.kson.jetbrains.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Interface for pluggable schema resolvers.
 *
 * Implementations provide different strategies for finding the schema that applies
 * to a given KSON file. [KsonSchemaService] queries registered resolvers in order
 * after checking the document's `$schema` property.
 */
interface KsonSchemaResolver {
    fun getSchemaForFile(project: Project, file: VirtualFile): SchemaResolution?
}
