package org.kson.jetbrains.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.KsonCore

/**
 * Tests for [KsonContentAwareSchemaProvider] verifying dynamic per-file schema
 * resolution via `$schema` and conversion to JSON for IntelliJ's consumption.
 */
class KsonContentAwareSchemaProviderTest : BasePlatformTestCase() {

    fun testResolvesSchemaViaDollarSchema() {
        myFixture.addFileToProject("my-schema.schema.kson", """
            type: object
            properties:
              name:
                type: string
                .
              .
        """.trimIndent())

        val doc = myFixture.addFileToProject("doc.kson", """
            '${'$'}schema': './my-schema.schema.kson'
            name: "Alice"
        """.trimIndent())

        val provider = KsonContentAwareSchemaProvider()
        val schemaFile = provider.getSchemaFile(doc)

        assertNotNull("Should resolve schema for file with \$schema", schemaFile)

        // LightVirtualFile must have .json extension so IntelliJ parses it as JSON PSI
        assertTrue(
            "Schema file name should end with .json, got: ${schemaFile!!.name}",
            schemaFile.name.endsWith(".json")
        )

        // Verify the returned file contains valid JSON (not KSON)
        val content = String(schemaFile.contentsToByteArray(), Charsets.UTF_8)
        val jsonResult = KsonCore.parseToAst(content)
        assertFalse("Schema should be valid JSON", jsonResult.hasErrors())
        assertTrue("JSON output should use colons", content.contains(":"))
        assertTrue("JSON output should contain type", content.contains("\"type\""))
    }

    fun testReturnsNullForPlainKsonFile() {
        val doc = myFixture.addFileToProject("plain.kson", "key: val")

        val provider = KsonContentAwareSchemaProvider()
        val schemaFile = provider.getSchemaFile(doc)

        assertNull("Should return null for files without a schema", schemaFile)
    }

    fun testSkipsSchemaKsonFiles() {
        val schemaDoc = myFixture.addFileToProject("config.schema.kson", """
            type: object
        """.trimIndent())

        val provider = KsonContentAwareSchemaProvider()
        val schemaFile = provider.getSchemaFile(schemaDoc)

        assertNull(
            "Should skip .schema.kson files (handled by KsonMetaSchemaProvider)",
            schemaFile
        )
    }

    fun testReturnsNullForNonKsonFile() {
        val jsonDoc = myFixture.addFileToProject("doc.json", "{}")

        val provider = KsonContentAwareSchemaProvider()
        val schemaFile = provider.getSchemaFile(jsonDoc)

        assertNull("Should return null for non-KSON files", schemaFile)
    }

    fun testConvertedSchemaIsValidJson() {
        // Schema written in KSON syntax (no quotes on keys, dot terminators)
        myFixture.addFileToProject("typed.schema.kson", """
            type: object
            properties:
              count:
                type: integer
                .
              .
            required:
              - count
              =
        """.trimIndent())

        val doc = myFixture.addFileToProject("data.kson", """
            '${'$'}schema': './typed.schema.kson'
            count: 42
        """.trimIndent())

        val provider = KsonContentAwareSchemaProvider()
        val schemaFile = provider.getSchemaFile(doc)

        assertNotNull("Should resolve KSON-syntax schema", schemaFile)
        val content = String(schemaFile!!.contentsToByteArray(), Charsets.UTF_8)

        // Verify it's valid JSON with JSON-style formatting
        assertTrue("Should have opening brace", content.trimStart().startsWith("{"))
        assertTrue("Should have closing brace", content.trimEnd().endsWith("}"))

        // Verify semantic content is preserved
        val ast = KsonCore.parseToAst(content)
        assertFalse("Converted JSON should parse without errors", ast.hasErrors())
    }
}
