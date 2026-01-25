package org.kson

import kotlin.test.*

/**
 * Unit tests for [SchemaRegistry]
 *
 * These tests verify the schema registration, lookup, and notification functionality.
 */
class SchemaRegistryTest {

    @BeforeTest
    fun setup() {
        SchemaRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        SchemaRegistry.setOnChangeListener(null)
        SchemaRegistry.clear()
    }

    // region Registration

    @Test
    fun testRegisterExtension_addsSchemas() {
        val schema = ExtensionSchema(
            schemaUri = "test://schema/myschema",
            schemaContent = "{ type: object }",
            fileExtensions = arrayOf(".myext"),
            fileMatch = arrayOf()
        )

        SchemaRegistry.registerExtension("test-extension", arrayOf(schema))

        val allSchemas = SchemaRegistry.getAllSchemas()
        assertEquals(1, allSchemas.size)
        assertEquals("test://schema/myschema", allSchemas[0].schemaUri)
    }

    @Test
    fun testRegisterExtension_replacesExistingSchemas() {
        val schema1 = ExtensionSchema("test://v1", "v1", arrayOf(".v1"), arrayOf())
        val schema2 = ExtensionSchema("test://v2", "v2", arrayOf(".v2"), arrayOf())

        SchemaRegistry.registerExtension("test-extension", arrayOf(schema1))
        SchemaRegistry.registerExtension("test-extension", arrayOf(schema2))

        val allSchemas = SchemaRegistry.getAllSchemas()
        assertEquals(1, allSchemas.size)
        assertEquals("test://v2", allSchemas[0].schemaUri)
    }

    @Test
    fun testUnregisterExtension_removesSchemas() {
        val schema = ExtensionSchema("test://schema", "{}", arrayOf(".ext"), arrayOf())
        SchemaRegistry.registerExtension("test-extension", arrayOf(schema))

        SchemaRegistry.unregisterExtension("test-extension")

        assertEquals(0, SchemaRegistry.getAllSchemas().size)
    }

    @Test
    fun testUnregisterExtension_doesNothingForUnknownExtension() {
        val schema = ExtensionSchema("test://schema", "{}", arrayOf(".ext"), arrayOf())
        SchemaRegistry.registerExtension("test-extension", arrayOf(schema))

        SchemaRegistry.unregisterExtension("unknown-extension")

        assertEquals(1, SchemaRegistry.getAllSchemas().size)
    }

    // endregion

    // region File Extension Matching

    @Test
    fun testGetSchemaForFile_matchesFileExtension() {
        val schema = ExtensionSchema(
            schemaUri = "test://myschema",
            schemaContent = "{ type: object }",
            fileExtensions = arrayOf(".myext"),
            fileMatch = arrayOf()
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("file:///path/to/file.myext")

        assertNotNull(result)
        assertEquals("test://myschema", result.schemaUri)
    }

    @Test
    fun testGetSchemaForFile_matchesFileExtensionWithKsonSuffix() {
        val schema = ExtensionSchema(
            schemaUri = "test://myschema",
            schemaContent = "{ type: object }",
            fileExtensions = arrayOf(".myext"),
            fileMatch = arrayOf()
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("file:///path/to/file.myext.kson")

        assertNotNull(result)
        assertEquals("test://myschema", result.schemaUri)
    }

    @Test
    fun testGetSchemaForFile_noMatchReturnsNull() {
        val schema = ExtensionSchema(
            schemaUri = "test://myschema",
            schemaContent = "{ type: object }",
            fileExtensions = arrayOf(".myext"),
            fileMatch = arrayOf()
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("file:///path/to/file.other")

        assertNull(result)
    }

    // endregion

    // region Glob Pattern Matching

    @Test
    fun testGetSchemaForFile_matchesGlobPattern() {
        val schema = ExtensionSchema(
            schemaUri = "test://config",
            schemaContent = "{ type: object }",
            fileExtensions = arrayOf(),
            fileMatch = arrayOf("config/app.kson")
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("file:///project/config/app.kson")

        assertNotNull(result)
        assertEquals("test://config", result.schemaUri)
    }

    @Test
    fun testGetSchemaForFile_matchesDoubleStarGlob() {
        val schema = ExtensionSchema(
            schemaUri = "test://config",
            schemaContent = "{ type: object }",
            fileExtensions = arrayOf(),
            fileMatch = arrayOf("**/config.kson")
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("file:///deep/nested/path/config.kson")

        assertNotNull(result)
    }

    @Test
    fun testGetSchemaForFile_prioritizesFileExtensionOverGlob() {
        val extSchema = ExtensionSchema(
            schemaUri = "test://ext",
            schemaContent = "extension",
            fileExtensions = arrayOf(".myext"),
            fileMatch = arrayOf()
        )
        val globSchema = ExtensionSchema(
            schemaUri = "test://glob",
            schemaContent = "glob",
            fileExtensions = arrayOf(),
            fileMatch = arrayOf("*.myext")
        )
        SchemaRegistry.registerExtension("ext1", arrayOf(extSchema))
        SchemaRegistry.registerExtension("ext2", arrayOf(globSchema))

        val result = SchemaRegistry.getSchemaForFile("file:///test/file.myext")

        assertNotNull(result)
        assertEquals("test://ext", result.schemaUri)
    }

    // endregion

    // region Version and Notifications

    @Test
    fun testGetVersion_incrementsOnRegister() {
        val initialVersion = SchemaRegistry.getVersion()

        SchemaRegistry.registerExtension("test", arrayOf())

        assertTrue(SchemaRegistry.getVersion() > initialVersion)
    }

    @Test
    fun testGetVersion_incrementsOnUnregister() {
        SchemaRegistry.registerExtension("test", arrayOf())
        val versionAfterRegister = SchemaRegistry.getVersion()

        SchemaRegistry.unregisterExtension("test")

        assertTrue(SchemaRegistry.getVersion() > versionAfterRegister)
    }

    @Test
    fun testSetOnChangeListener_calledOnRegister() {
        var notifiedExtensionId: String? = null
        SchemaRegistry.setOnChangeListener { extensionId ->
            notifiedExtensionId = extensionId
        }

        SchemaRegistry.registerExtension("my-extension", arrayOf())

        assertEquals("my-extension", notifiedExtensionId)
    }

    @Test
    fun testSetOnChangeListener_calledOnUnregister() {
        SchemaRegistry.registerExtension("my-extension", arrayOf())

        var notifiedExtensionId: String? = null
        SchemaRegistry.setOnChangeListener { extensionId ->
            notifiedExtensionId = extensionId
        }

        SchemaRegistry.unregisterExtension("my-extension")

        assertEquals("my-extension", notifiedExtensionId)
    }

    @Test
    fun testSetOnChangeListener_notCalledForNonExistentUnregister() {
        var listenerCalled = false
        SchemaRegistry.setOnChangeListener { _ ->
            listenerCalled = true
        }

        SchemaRegistry.unregisterExtension("non-existent")

        assertFalse(listenerCalled)
    }

    // endregion

    // region URI Parsing

    @Test
    fun testGetSchemaForFile_handlesPlainPath() {
        val schema = ExtensionSchema(
            schemaUri = "test://schema",
            schemaContent = "{}",
            fileExtensions = arrayOf(".kson"),
            fileMatch = arrayOf()
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("/path/to/file.kson")

        assertNotNull(result)
    }

    @Test
    fun testGetSchemaForFile_handlesWindowsStyleUri() {
        val schema = ExtensionSchema(
            schemaUri = "test://schema",
            schemaContent = "{}",
            fileExtensions = arrayOf(".kson"),
            fileMatch = arrayOf()
        )
        SchemaRegistry.registerExtension("test", arrayOf(schema))

        val result = SchemaRegistry.getSchemaForFile("file:///C:/Users/test/file.kson")

        assertNotNull(result)
    }

    // endregion

    // region Clear

    @Test
    fun testClear_removesAllSchemas() {
        SchemaRegistry.registerExtension("ext1", arrayOf(
            ExtensionSchema("test://1", "{}", arrayOf(".ext1"), arrayOf())
        ))
        SchemaRegistry.registerExtension("ext2", arrayOf(
            ExtensionSchema("test://2", "{}", arrayOf(".ext2"), arrayOf())
        ))

        SchemaRegistry.clear()

        assertEquals(0, SchemaRegistry.getAllSchemas().size)
    }

    // endregion
}
