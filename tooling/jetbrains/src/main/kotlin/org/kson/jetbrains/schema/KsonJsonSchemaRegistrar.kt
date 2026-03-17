package org.kson.jetbrains.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Startup activity that registers [KsonJsonSchemaResolver] with [KsonSchemaService].
 *
 * This is declared in `kson-withJson.xml` so it only runs when the JSON plugin is present,
 * ensuring the IntelliJ JSON Schema Mappings UI integration is available.
 */
class KsonJsonSchemaRegistrar : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        KsonSchemaService.getInstance(project)
            .registerResolver(KsonJsonSchemaResolver())
    }
}
