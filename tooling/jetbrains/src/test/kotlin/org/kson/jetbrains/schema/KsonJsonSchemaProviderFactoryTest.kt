package org.kson.jetbrains.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import org.kson.KsonCore

/**
 * Tests for [KsonJsonSchemaProviderFactory] verifying:
 * - The bundled metaschema is correctly provided for `.schema.kson` files
 * - The `$schema` provider resolves schemas and converts them to JSON
 */
class KsonJsonSchemaProviderFactoryTest : BasePlatformTestCase() {

    private fun findProviderByName(name: String): JsonSchemaFileProvider {
        val factory = KsonJsonSchemaProviderFactory()
        val providers = factory.getProviders(project)
        return providers.first { it.getName() == name }
    }

    fun testProvidesTwoProviders() {
        val factory = KsonJsonSchemaProviderFactory()
        val providers = factory.getProviders(project)

        assertEquals("Should provide two providers", 2, providers.size)
        val names = providers.map { it.getName() }.toSet()
        assertTrue("Should include metaschema provider", "KSON Draft-07 Metaschema" in names)
        assertTrue("Should include \$schema provider", "KSON Schema" in names)
    }

    // -- Metaschema provider tests --

    fun testMetaSchemaAvailableForSchemaKsonFiles() {
        val provider = findProviderByName("KSON Draft-07 Metaschema")

        val schemaFile = myFixture.addFileToProject("config.schema.kson", "type: object")
        assertTrue(
            "Metaschema should be available for .schema.kson files",
            provider.isAvailable(schemaFile.virtualFile)
        )
    }

    fun testMetaSchemaNotAvailableForPlainKsonFiles() {
        val provider = findProviderByName("KSON Draft-07 Metaschema")

        val plainFile = myFixture.addFileToProject("config.kson", "key: val")
        assertFalse(
            "Metaschema should not be available for plain .kson files",
            provider.isAvailable(plainFile.virtualFile)
        )
    }

    fun testMetaSchemaNotAvailableForNonKsonFiles() {
        val provider = findProviderByName("KSON Draft-07 Metaschema")

        val jsonFile = myFixture.addFileToProject("schema.json", "{}")
        assertFalse(
            "Metaschema should not be available for .json files",
            provider.isAvailable(jsonFile.virtualFile)
        )
    }

    fun testMetaSchemaFileContainsValidJson() {
        val provider = findProviderByName("KSON Draft-07 Metaschema")
        val schemaFile = provider.getSchemaFile()

        assertNotNull("Schema file should not be null", schemaFile)
        val content = String(schemaFile!!.contentsToByteArray(), Charsets.UTF_8)

        val jsonResult = KsonCore.parseToAst(content)
        assertFalse("Metaschema JSON should parse without errors", jsonResult.hasErrors())
        assertTrue("Should contain schema draft reference", content.contains("json-schema.org"))
        assertTrue("Should contain 'definitions'", content.contains("definitions"))
    }

    fun testMetaSchemaIsEmbeddedType() {
        val provider = findProviderByName("KSON Draft-07 Metaschema")
        assertEquals(SchemaType.embeddedSchema, provider.getSchemaType())
    }

    // -- $schema provider tests --

    fun testDollarSchemaProviderAvailableForFileWithSchema() {
        myFixture.addFileToProject("my.schema.kson", "type: object")
        val doc = myFixture.addFileToProject("doc.kson", """
            '${'$'}schema': './my.schema.kson'
            key: val
        """.trimIndent())

        val provider = findProviderByName("KSON Schema")
        assertTrue(
            "\$schema provider should be available for files with \$schema",
            provider.isAvailable(doc.virtualFile)
        )
    }

    fun testDollarSchemaProviderResolvesSchemaAsJson() {
        myFixture.addFileToProject("my.schema.kson", """
            type: object
            properties:
              name:
                type: string
                .
              .
        """.trimIndent())
        val doc = myFixture.addFileToProject("doc2.kson", """
            '${'$'}schema': './my.schema.kson'
            name: "Alice"
        """.trimIndent())

        val provider = findProviderByName("KSON Schema")
        provider.isAvailable(doc.virtualFile) // triggers resolution

        val schemaFile = provider.getSchemaFile()
        assertNotNull("Should have resolved schema file", schemaFile)
        assertTrue(
            "Schema file should have .json extension, got: ${schemaFile!!.name}",
            schemaFile.name.endsWith(".json")
        )

        val content = String(schemaFile.contentsToByteArray(), Charsets.UTF_8)
        val parseResult = KsonCore.parseToJson(content)
        assertFalse("Converted schema should be valid JSON", parseResult.hasErrors())
        assertTrue("Should contain type property", content.contains("\"type\""))
    }

    fun testDollarSchemaProviderNotAvailableForPlainFile() {
        val doc = myFixture.addFileToProject("plain.kson", "key: val")

        val provider = findProviderByName("KSON Schema")
        assertFalse(
            "\$schema provider should not be available for files without \$schema",
            provider.isAvailable(doc.virtualFile)
        )
    }

    fun testDollarSchemaProviderSkipsSchemaKsonFiles() {
        val schemaDoc = myFixture.addFileToProject("config.schema.kson", "type: object")

        val provider = findProviderByName("KSON Schema")
        assertFalse(
            "\$schema provider should skip .schema.kson files",
            provider.isAvailable(schemaDoc.virtualFile)
        )
    }
}
