package org.kson.jetbrains.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.ide.JsonSchemaService

/**
 * Integration tests that verify KSON schemas are discoverable through IntelliJ's
 * [JsonSchemaService], which is what the status bar widget uses to find schemas.
 */
class KsonJsonSchemaWidgetIntegrationTest : BasePlatformTestCase() {

    fun testWidgetFindsMetaschemaForSchemaKsonFile() {
        val doc = myFixture.addFileToProject("config.schema.kson", "type: object")

        val service = JsonSchemaService.Impl.get(project)
        val schemas = service.getSchemaFilesForFile(doc.virtualFile)

        assertFalse(
            "Widget should find metaschema for .schema.kson files",
            schemas.isEmpty()
        )
    }

    fun testWidgetFindsSchemaViaDollarSchema() {
        myFixture.addFileToProject("my.schema.kson", "type: object")
        val doc = myFixture.addFileToProject("doc.kson", """
            '${'$'}schema': './my.schema.kson'
            key: val
        """.trimIndent())

        val service = JsonSchemaService.Impl.get(project)
        val schemas = service.getSchemaFilesForFile(doc.virtualFile)

        assertFalse(
            "Widget should find schema for files with \$schema",
            schemas.isEmpty()
        )
    }

    fun testWidgetShowsNoSchemaForPlainFile() {
        val doc = myFixture.addFileToProject("plain.kson", "key: val")

        val service = JsonSchemaService.Impl.get(project)
        val schemas = service.getSchemaFilesForFile(doc.virtualFile)

        assertTrue(
            "Widget should show no schema for plain .kson files",
            schemas.isEmpty()
        )
    }
}
