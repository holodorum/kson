package org.kson.schema

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [SchemaRegistry]
 *
 * These tests verify the schema registration functionality.
 * For lookup/query tests, see SchemaLookupTest in kson-tooling-lib.
 */
class SchemaRegistryTest {

    @BeforeTest
    fun setup() {
        SchemaRegistry.clear()
    }

    // A simple valid schema for testing
    private fun testSchema() = JsonBooleanSchema(true)

    // region Registration

    @Test
    fun testRegisterExtension_addsSchemas() {
        val schema = ExtensionSchema(
            schemaUri = "test://schema/myschema",
            schema = testSchema(),
            fileExtensions = listOf(".myext")
        )

        SchemaRegistry.registerExtension("test-extension", listOf(schema))

        val allSchemas = SchemaRegistry.getAllSchemas()
        assertEquals(1, allSchemas.size)
        assertEquals("test://schema/myschema", allSchemas[0].schemaUri)
    }

    @Test
    fun testRegisterExtension_replacesExistingSchemas() {
        val schema1 = ExtensionSchema("test://v1", testSchema(), listOf(".v1"))
        val schema2 = ExtensionSchema("test://v2", testSchema(), listOf(".v2"))

        SchemaRegistry.registerExtension("test-extension", listOf(schema1))
        SchemaRegistry.registerExtension("test-extension", listOf(schema2))

        val allSchemas = SchemaRegistry.getAllSchemas()
        assertEquals(1, allSchemas.size)
        assertEquals("test://v2", allSchemas[0].schemaUri)
    }

    @Test
    fun testUnregisterExtension_removesSchemas() {
        val schema = ExtensionSchema("test://schema", testSchema(), listOf(".ext"))
        SchemaRegistry.registerExtension("test-extension", listOf(schema))

        SchemaRegistry.unregisterExtension("test-extension")

        assertEquals(0, SchemaRegistry.getAllSchemas().size)
    }

    @Test
    fun testUnregisterExtension_doesNothingForUnknownExtension() {
        val schema = ExtensionSchema("test://schema", testSchema(), listOf(".ext"))
        SchemaRegistry.registerExtension("test-extension", listOf(schema))

        SchemaRegistry.unregisterExtension("unknown-extension")

        assertEquals(1, SchemaRegistry.getAllSchemas().size)
    }

    // endregion

    // region Clear

    @Test
    fun testClear_removesAllSchemas() {
        SchemaRegistry.registerExtension("ext1", listOf(
            ExtensionSchema("test://1", testSchema(), listOf(".ext1"))
        ))
        SchemaRegistry.registerExtension("ext2", listOf(
            ExtensionSchema("test://2", testSchema(), listOf(".ext2"))
        ))

        SchemaRegistry.clear()

        assertEquals(0, SchemaRegistry.getAllSchemas().size)
    }

    // endregion
}
