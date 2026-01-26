package org.kson

import org.kson.schema.ExtensionSchema
import org.kson.schema.JsonBooleanSchema
import org.kson.schema.SchemaRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [SchemaLookup]
 *
 * These tests verify the schema lookup functionality including file extension
 * matching and URI parsing.
 */
class SchemaLookupTest {

    @BeforeTest
    fun setup() {
        SchemaRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        SchemaRegistry.clear()
    }

    // A simple valid schema for testing
    private fun testSchema() = JsonBooleanSchema(true)

    // region File Extension Matching

    @Test
    fun testGetSchemaForFile_matchesFileExtension() {
        val schema = ExtensionSchema(
            schemaUri = "test://myschema",
            schema = testSchema(),
            fileExtensions = listOf(".myext")
        )
        SchemaRegistry.registerExtension("test", listOf(schema))

        val result = SchemaLookup.getSchemaForFile("file:///path/to/file.myext")

        assertNotNull(result)
        assertEquals("test://myschema", result.schemaUri)
    }

    @Test
    fun testGetSchemaForFile_matchesFileExtensionWithKsonSuffix() {
        val schema = ExtensionSchema(
            schemaUri = "test://myschema",
            schema = testSchema(),
            fileExtensions = listOf(".myext")
        )
        SchemaRegistry.registerExtension("test", listOf(schema))

        val result = SchemaLookup.getSchemaForFile("file:///path/to/file.myext.kson")

        assertNotNull(result)
        assertEquals("test://myschema", result.schemaUri)
    }

    @Test
    fun testGetSchemaForFile_noMatchReturnsNull() {
        val schema = ExtensionSchema(
            schemaUri = "test://myschema",
            schema = testSchema(),
            fileExtensions = listOf(".myext")
        )
        SchemaRegistry.registerExtension("test", listOf(schema))

        val result = SchemaLookup.getSchemaForFile("file:///path/to/file.other")

        assertNull(result)
    }

    // endregion

    // region URI Parsing

    @Test
    fun testGetSchemaForFile_handlesPlainPath() {
        val schema = ExtensionSchema(
            schemaUri = "test://schema",
            schema = testSchema(),
            fileExtensions = listOf(".kson")
        )
        SchemaRegistry.registerExtension("test", listOf(schema))

        val result = SchemaLookup.getSchemaForFile("/path/to/file.kson")

        assertNotNull(result)
    }

    @Test
    fun testGetSchemaForFile_handlesWindowsStyleUri() {
        val schema = ExtensionSchema(
            schemaUri = "test://schema",
            schema = testSchema(),
            fileExtensions = listOf(".kson")
        )
        SchemaRegistry.registerExtension("test", listOf(schema))

        val result = SchemaLookup.getSchemaForFile("file:///C:/Users/test/file.kson")

        assertNotNull(result)
    }

    // endregion
}
