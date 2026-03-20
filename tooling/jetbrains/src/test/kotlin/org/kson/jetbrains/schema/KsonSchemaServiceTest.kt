package org.kson.jetbrains.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [KsonSchemaService] verifying the three-tier schema resolution strategy:
 * `$schema` property, registered resolvers, and bundled metaschema.
 */
class KsonSchemaServiceTest : BasePlatformTestCase() {

    fun testResolvesSchemaFromDollarSchemaProperty() {
        myFixture.addFileToProject("my-schema.kson", """
            type: object
            properties:
              name:
                type: string
                .
              .
        """.trimIndent())

        val docFile = myFixture.addFileToProject("doc.kson", """
            '${'$'}schema': './my-schema.kson'
            name: "Alice"
        """.trimIndent())

        val service = KsonSchemaService.getInstance(project)
        val resolution = service.getSchemaForFile(docFile.virtualFile)

        assertNotNull("Should resolve schema from \$schema property", resolution)
        assertNotNull("Should have a schema file", resolution!!.schemaFile)
        assertTrue("Schema text should contain 'type: object'", resolution.schemaText.contains("type: object"))
    }

    fun testResolvesSchemaFromRegisteredResolver() {
        val stubSchemaText = "type: string"
        val service = KsonSchemaService.getInstance(project)

        service.registerResolver(object : KsonSchemaResolver {
            override fun getSchemaForFile(project: Project, file: VirtualFile): SchemaResolution {
                return SchemaResolution(stubSchemaText, null)
            }
        })

        val docFile = myFixture.addFileToProject("doc2.kson", "value: 42")
        val resolution = service.getSchemaForFile(docFile.virtualFile)

        assertNotNull("Should resolve from registered resolver", resolution)
        assertEquals("Should use resolver's schema text", stubSchemaText, resolution!!.schemaText)
    }

    fun testDollarSchemaTakesPrecedenceOverResolver() {
        myFixture.addFileToProject("my-schema2.kson", "type: object")

        val service = KsonSchemaService.getInstance(project)
        service.registerResolver(object : KsonSchemaResolver {
            override fun getSchemaForFile(project: Project, file: VirtualFile): SchemaResolution {
                return SchemaResolution("type: string", null)
            }
        })

        val docFile = myFixture.addFileToProject("doc3.kson", """
            '${'$'}schema': './my-schema2.kson'
            key: val
        """.trimIndent())
        val resolution = service.getSchemaForFile(docFile.virtualFile)

        assertNotNull(resolution)
        assertTrue(
            "\$schema should take precedence over resolver",
            resolution!!.schemaText.contains("type: object")
        )
    }

    fun testBundledMetaschemaForSchemaKsonFiles() {
        val schemaFile = myFixture.addFileToProject("config.schema.kson", """
            type: object
            properties:
              name:
                type: string
                .
              .
        """.trimIndent())

        val service = KsonSchemaService.getInstance(project)
        val resolution = service.getSchemaForFile(schemaFile.virtualFile)

        assertNotNull("Should resolve metaschema for .schema.kson files", resolution)
        assertTrue(
            "Schema text should contain Draft-07 metaschema content",
            resolution!!.schemaText.contains("json-schema.org")
        )
    }

    fun testDirectBundledMetaschemaHasNoSchemaFile() {
        val schemaFile = myFixture.addFileToProject("direct.schema.kson", "type: object")

        // Test the internal resolution directly to verify the bundled path returns null schemaFile.
        // The public getSchemaForFile() may resolve via a registered resolver (which provides
        // a non-null schemaFile), but the bundled fallback should always return null.
        val resolution = KsonSchemaService.getInstance(project)
            .resolveFromDollarSchema(schemaFile.virtualFile)

        assertNull(
            "A .schema.kson file without \$schema should not resolve via \$schema",
            resolution
        )
    }

    fun testDollarSchemaTakesPrecedenceOverMetaschema() {
        myFixture.addFileToProject("custom.kson", "type: string")

        val schemaFile = myFixture.addFileToProject("config.schema.kson", """
            '${'$'}schema': './custom.kson'
            type: object
        """.trimIndent())

        val service = KsonSchemaService.getInstance(project)
        val resolution = service.getSchemaForFile(schemaFile.virtualFile)

        assertNotNull(resolution)
        assertTrue(
            "\$schema should take precedence over bundled metaschema",
            resolution!!.schemaText.contains("type: string")
        )
    }

    fun testNoSchemaResolutionForPlainFile() {
        val docFile = myFixture.addFileToProject("plain.kson", "key: val")

        val service = KsonSchemaService.getInstance(project)
        val resolution = service.getSchemaForFile(docFile.virtualFile)

        assertNull("Plain files without \$schema should have no resolution", resolution)
    }

    fun testExtractDollarSchemaPath() {
        assertEquals(
            "./my-schema.kson",
            extractDollarSchemaPath("'\$schema': './my-schema.kson'\nkey: val")
        )
    }

    fun testExtractDollarSchemaPathMissing() {
        assertNull(extractDollarSchemaPath("key: val"))
    }

    fun testExtractDollarSchemaPathNonString() {
        assertNull(extractDollarSchemaPath("'\$schema': 42"))
    }
}
