package org.kson

import org.kson.navigation.CompletionItem
import org.kson.navigation.CompletionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompletionTest {

    /**
     * Helper to get completions at a position
     */
    private fun getCompletions(documentRoot: String, schemaValue: String, line: Int, column: Int): List<CompletionItem>? {
        return KsonTooling.getCompletionsAtLocation(documentRoot, schemaValue, line, column)
    }

    @Test
    fun testEnumValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    status: {
                        type: string
                        description: "Current status"
                        enum: ["active", "inactive", "pending"]
                    }
                }
            }
        """

        val document = """
            {
                status: "active"
            }
        """.trimIndent()

        // Get completions at the "active" value position
        val completions = getCompletions(document, schema, line = 1, column = 20)

        assertNotNull(completions, "Should return completions for enum value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that all enum values are present
        val labels = completions.map { it.label }
        assertTrue("active" in labels, "Should include 'active'")
        assertTrue("inactive" in labels, "Should include 'inactive'")
        assertTrue("pending" in labels, "Should include 'pending'")

        // All should be VALUE kind
        assertTrue(completions.all { it.kind == CompletionKind.VALUE }, "All should be VALUE completions")
    }

    @Test
    fun testBooleanValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    enabled: {
                        type: boolean
                        description: "Feature toggle"
                    }
                }
            }
        """

        // language="kson"
        val document = """
            {
                enabled: true
            }
        """.trimIndent()

        // Get completions at the boolean value position
        val completions = getCompletions(document, schema, line = 1, column = 15)

        assertNotNull(completions, "Should return completions for boolean value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that both boolean values are present
        val labels = completions.map { it.label }
        assertTrue("true" in labels, "Should include 'true'")
        assertTrue("false" in labels, "Should include 'false'")

        // Should have exactly 2 items
        assertEquals(2, completions.size, "Should have exactly 2 boolean values")
    }

    @Test
    fun testNullValueCompletion() {
        val schema = """
            {
                type: object
                properties: {
                    optional: {
                        type: "null"
                        description: "Optional field"
                    }
                }
            }
        """

        // language="kson"
        val document = """
            {
                optional: null
            }
        """.trimIndent()

        // Get completions at the null value position
        val completions = getCompletions(document, schema, line = 1, column = 16)

        assertNotNull(completions, "Should return completions for null value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that null is present
        val labels = completions.map { it.label }
        assertTrue("null" in labels, "Should include 'null'")
    }

    @Test
    fun testUnionTypeBooleanAndNullCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    setting: {
                        type: ["boolean", "null"]
                        description: "Optional boolean setting"
                    }
                }
            }
        """

        val document = """
            {
                setting: true
            }
        """.trimIndent()

        // Get completions at the value position
        val completions = getCompletions(document, schema, line = 1, column = 14)

        assertNotNull(completions, "Should return completions for union type")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that boolean and null values are present
        val labels = completions.map { it.label }
        assertTrue("true" in labels, "Should include 'true'")
        assertTrue("false" in labels, "Should include 'false'")
        assertTrue("null" in labels, "Should include 'null'")

        // Should have 3 items total
        assertEquals(3, completions.size, "Should have 3 values (true, false, null)")
    }

    @Test
    fun testEnumWithDocumentation() {
        val schema = """
            {
                type: object
                properties: {
                    level: {
                        type: string
                        title: "Log Level"
                        description: "The logging verbosity level"
                        enum: ["debug", "info", "warn", "error"]
                    }
                }
            }
        """

        val document = """
            {
                level: "info"
            }
        """.trimIndent()

        // Get completions at the value position
        val completions = getCompletions(document, schema, line = 1, column = 14)

        assertNotNull(completions, "Should return completions")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that completions have documentation
        val withDocs = completions.filter { it.documentation != null }
        assertTrue(withDocs.isNotEmpty(), "At least one completion should have documentation")

        // The documentation should include the schema info
        val firstWithDoc = withDocs.first()
        assertTrue(
            firstWithDoc.documentation!!.contains("Log Level") ||
                    firstWithDoc.documentation.contains("logging verbosity"),
            "Documentation should include schema info"
        )
    }

    @Test
    fun testCompletionsWithDetail() {
        // language="kson"
        val schema = """
            {
                type: object
                properties: {
                    priority: {
                        type: string
                        enum: ["low", "medium", "high"]
                    }
                }
            }
        """.trimIndent()

        val document = """
            {
                priority: "low"
            }
        """.trimIndent()

        // Get completions
        val completions = getCompletions(document, schema, line = 1, column = 15)

        assertNotNull(completions, "Should return completions")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // All completions should have a detail field
        assertTrue(
            completions.all { it.detail != null },
            "All completions should have detail text"
        )
    }

    @Test
    fun testNoCompletionsForStringWithoutEnum() {
        val schema = """
            {
                type: object
                properties: {
                    name: {
                        type: string
                        description: "User name"
                    }
                }
            }
        """

        val document = """
            {
                name: "John"
            }
        """.trimIndent()

        // Get completions at a plain string value
        val completions = getCompletions(document, schema, line = 1, column = 15)

        // Should return empty list or null (no specific completions for plain strings)
        assertTrue(
            completions == null || completions.isEmpty(),
            "Should not have completions for plain string type"
        )
    }

    @Test
    fun testNestedPropertyEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    user: {
                        type: object
                        properties: {
                            role: {
                                type: string
                                enum: ["admin", "user", "guest"]
                            }
                        }
                    }
                }
            }
        """

        // language="kson"
        val document = """
            {
                user: {
                    role: "admin"
                }
            }
        """.trimIndent()

        // Get completions at the nested enum value
        val completions = getCompletions(document, schema, line = 2, column = 18)

        assertNotNull(completions, "Should return completions for nested enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("admin" in labels, "Should include 'admin'")
        assertTrue("user" in labels, "Should include 'user'")
        assertTrue("guest" in labels, "Should include 'guest'")
    }

    @Test
    fun testArrayItemEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    tags: {
                        type: array
                        items: {
                            type: string
                            enum: ["red", "green", "blue"]
                        }
                    }
                }
            }
        """

        val document = """
            {
                tags: ["red", "blue"]
            }
        """.trimIndent()

        // Get completions at the first array item
        val completions = getCompletions(document, schema, line = 1, column = 23)

        assertNotNull(completions, "Should return completions for array item enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("red" in labels, "Should include 'red'")
        assertTrue("green" in labels, "Should include 'green'")
        assertTrue("blue" in labels, "Should include 'blue'")
    }

    @Test
    fun testNumericEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    version: {
                        type: number
                        enum: [1, 2, 3]
                    }
                }
            }
        """

        val document = """
            {
                version: 1
            }
        """.trimIndent()

        // Get completions at the numeric value
        val completions = getCompletions(document, schema, line = 1, column = 14)

        assertNotNull(completions, "Should return completions for numeric enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("1" in labels, "Should include '1'")
        assertTrue("2" in labels, "Should include '2'")
        assertTrue("3" in labels, "Should include '3'")
    }

    @Test
    fun testMixedTypeEnumCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    value: {
                        enum: ["auto", 100, true, null]
                    }
                }
            }
        """

        // language="kson"
        val document = """
            {
                value: "auto"
            }
        """.trimIndent()

        // Get completions at the value
        val completions = getCompletions(document, schema, line = 1, column = 15)

        assertNotNull(completions, "Should return completions for mixed-type enum")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("auto" in labels, "Should include 'auto'")
        assertTrue("100" in labels, "Should include '100'")
        assertTrue("true" in labels, "Should include 'true'")
        assertTrue("null" in labels, "Should include 'null'")

        // Should have 4 items
        assertEquals(4, completions.size, "Should have all 4 enum values")
    }

    @Test
    fun testCompletionsAtInvalidPosition() {
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                }
            }
        """

        val document = """
            {
                name: "test"
            }
        """

        // Get completions at an invalid position (outside the document)
        val completions = getCompletions(document, schema, line = 10, column = 0)

        // Should return null or empty (no value at this location)
        assertTrue(
            completions == null || completions.isEmpty(),
            "Should not return completions at invalid position"
        )
    }

    @Test
    fun testNoSchemaReturnsNull() {
        val document = """
            {
                name: "test"
            }
        """

        // Try to get completions with empty schema
        val completions = getCompletions(document, "", line = 1, column = 18)

        // Should return null when schema is invalid
        assertEquals(completions, null, "Should return null when schema is empty/invalid")
    }

    @Test
    fun testLocationOnEmptyValue(){
       val schema = """
            {
                type: object
                properties: {
                    key: {
                      type: string
                      enum: ["active", "inactive", "pending"]
                    }
                }
            }
        """

        val document = """
            key: 
        """.trimIndent()

        // Try to get completions with being on empty location
        val completions = getCompletions(document, schema, line = 0, column = 5)

        assertNotNull(completions, "Should return completions for enum value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Check that all enum values are present
        val labels = completions.map { it.label }
        assertTrue("active" in labels, "Should include 'active'")
        assertTrue("inactive" in labels, "Should include 'inactive'")
        assertTrue("pending" in labels, "Should include 'pending'")
    }

    @Test
    fun testObjectValueProvidesPropertyCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    user: {
                        type: object
                        properties: {
                            name: { type: string }
                            age: { type: number }
                            email: { type: string }
                        }
                    }
                }
            }
        """

        val document = """
            {
                user: {
                    name: "John"
                }
            }
        """.trimIndent()

        // Get completions at the user value position (the object value)
        val completions = getCompletions(document, schema, line = 1, column = 11)

        assertNotNull(completions, "Should return completions for object value")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should suggest properties from the object schema
        val labels = completions.map { it.label }
        assertTrue("name" in labels, "Should include 'name' property")
        assertTrue("age" in labels, "Should include 'age' property")
        assertTrue("email" in labels, "Should include 'email' property")

        // All should be PROPERTY kind
        assertTrue(completions.all { it.kind == CompletionKind.PROPERTY }, "All should be PROPERTY completions")
    }

    @Test
    fun testObjectTypeWithoutExplicitType() {
        val schema = """
            {
                type: object
                properties: {
                    config: {
                        properties: {
                            debug: { type: boolean }
                            verbose: { type: boolean }
                        }
                    }
                }
            }
        """

        val document = """
            {
                config: {
                    debug: true
                }
            }
        """.trimIndent()

        // Get completions at the config value (object without explicit type)
        val completions = getCompletions(document, schema, line = 1, column = 13)

        assertNotNull(completions, "Should return completions even without explicit type")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // Should infer object type from presence of properties
        val labels = completions.map { it.label }
        assertTrue("debug" in labels, "Should include 'debug' property")
        assertTrue("verbose" in labels, "Should include 'verbose' property")
    }

    @Test
    fun testUnionTypeWithObject() {
        val schema = """
            {
                type: object
                properties: {
                    value: {
                        type: ["object", "null"]
                        properties: {
                            x: { type: number }
                            y: { type: number }
                        }
                    }
                }
            }
        """

        val document = """
            {
                value: {
                    x: 10
                }
            }
        """.trimIndent()

        // Get completions at the value position (union type with object)
        val completions = getCompletions(document, schema, line = 1, column = 12)

        assertNotNull(completions, "Should return completions for union with object")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        // When object is in union type, should provide property completions
        val labels = completions.map { it.label }
        assertTrue("x" in labels, "Should include 'x' property")
        assertTrue("y" in labels, "Should include 'y' property")
    }

    @Test
    fun testNestedObjectValueCompletions() {
        val schema = """
            {
                type: object
                properties: {
                    app: {
                        type: object
                        properties: {
                            settings: {
                                type: object
                                properties: {
                                    theme: {
                                        type: string
                                        enum: ["light", "dark"]
                                    }
                                    fontSize: { type: number }
                                }
                            }
                        }
                    }
                }
            }
        """

        val document = """
            {
                app: {
                    settings: {
                        
                    }
                }
            }
        """.trimIndent()

        // Get completions at the nested settings object value
        val completions = getCompletions(document, schema, line = 2, column = 19)

        assertNotNull(completions, "Should return completions for nested object")
        assertTrue(completions.isNotEmpty(), "Should have completion items")

        val labels = completions.map { it.label }
        assertTrue("theme" in labels, "Should include 'theme' property")
        assertTrue("fontSize" in labels, "Should include 'fontSize' property")
    }
}