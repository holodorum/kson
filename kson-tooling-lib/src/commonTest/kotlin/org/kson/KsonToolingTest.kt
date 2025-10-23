package org.kson

import org.kson.value.KsonValueNavigation
import kotlin.test.*

/**
 * Tests for [KsonTooling] hover information functionality
 */
class KsonToolingTest {

    @Test
    fun testGetSchemaInfoAtLocation_simpleStringProperty() {
        val document = """
            name: John
            age: 30
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "The person's name"
                },
                "age": {
                  "type": "number",
                  "description": "The person's age"
                }
              }
            }
        """.trimIndent()

        // Hover over "John" (line 0, column 6)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 6)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("The person's name"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_numberProperty() {
        val document = """
            name: John
            age: 30
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                },
                "age": {
                  "type": "number",
                  "description": "The person's age",
                  "minimum": 0,
                  "maximum": 120
                }
              }
            }
        """.trimIndent()

        // Hover over "30" (line 1, column 5)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 1, 5)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("The person's age"))
        assertTrue(hoverInfo.contains("*Type:* `number`"))
        assertTrue(hoverInfo.contains("*Minimum:* 0"))
        assertTrue(hoverInfo.contains("*Maximum:* 120"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withTitle() {
        val document = """
            username: johndoe
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "username": {
                  "type": "string",
                  "title": "Username",
                  "description": "The user's unique identifier"
                }
              }
            }
        """.trimIndent()

        // Hover over "johndoe" (line 0, column 10)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 10)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("**Username**"))
        assertTrue(hoverInfo.contains("The user's unique identifier"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withEnum() {
        val document = """
            status: active
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "status": {
                  "type": "string",
                  "enum": ["active", "inactive", "pending"]
                }
              }
            }
        """.trimIndent()

        // Hover over "active" (line 0, column 8)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 8)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Allowed values:*"))
        assertTrue(hoverInfo.contains("`active`"))
        assertTrue(hoverInfo.contains("`inactive`"))
        assertTrue(hoverInfo.contains("`pending`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withPattern() {
        val document = """
            email: 'user@example.com'
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "email": {
                  "type": "string",
                  "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
                }
              }
            }
        """.trimIndent()

        // Debug: Let's parse and check the document structure
        val parsedDoc = KsonCore.parseToAst(document).ksonValue
        println("Parsed document: $parsedDoc")

        // Try hovering at the beginning of the email value
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 7)

        if (hoverInfo == null) {
            println("Hover info is null at position (0, 7)")
            // Try to find the actual value node
            val valueNode = KsonValueNavigation.navigateByTokens(parsedDoc!!, listOf("email"))
            println("Value node: $valueNode")
            println("Value node location: ${valueNode?.location}")
        }

        assertNotNull(hoverInfo, "Expected hover info but got null")
        assertTrue(hoverInfo.contains("*Pattern:*"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_nestedObject() {
        val document = """
            person:
              name: Alice
              age: 25
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "person": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Person's name"
                    },
                    "age": {
                      "type": "number",
                      "description": "Person's age"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        // Hover over "Alice" (line 1, column 8)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 1, 8)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Person's name"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_arrayItems() {
        val document = """
            tags:
              - kotlin
              - multiplatform
              - json
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "tags": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "description": "A tag value",
                    "minLength": 2,
                    "maxLength": 50
                  }
                }
              }
            }
        """.trimIndent()

        // Hover over "kotlin" (line 1, column 4)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 1, 4)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("A tag value"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
        assertTrue(hoverInfo.contains("*Min length:* 2"))
        assertTrue(hoverInfo.contains("*Max length:* 50"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_withDefault() {
        val document = """
            timeout: 5000
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "timeout": {
                  "type": "number",
                  "description": "Request timeout in milliseconds",
                  "default": 3000
                }
              }
            }
        """.trimIndent()

        // Hover over "5000" (line 0, column 9)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 9)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Request timeout in milliseconds"))
        assertTrue(hoverInfo.contains("*Default:* `3000`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_stringLengthConstraints() {
        val document = """
            password: secret123
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "password": {
                  "type": "string",
                  "minLength": 8,
                  "maxLength": 32
                }
              }
            }
        """.trimIndent()

        // Hover over "secret123" (line 0, column 10)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 10)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Min length:* 8"))
        assertTrue(hoverInfo.contains("*Max length:* 32"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_arrayConstraints() {
        val document = """
            items:
              - first
              - second
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 10,
                  "items": {
                    "type": "string"
                  }
                }
              }
            }
        """.trimIndent()

        // Hover over "first" (line 1, column 4)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 1, 4)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }
    
    @Test
    fun testGetSchemaInfoAtLocation_noSchemaForProperty() {
        val document = """
            undefinedProp: value
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {}
            }
        """.trimIndent()

        // Hover over "value" (line 0, column 15)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 15)

        // Should return null when no schema matches
        assertNull(hoverInfo)
    }

    @Test
    fun testGetSchemaInfoAtLocation_additionalProperties() {
        val document = """
            customField: customValue
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "additionalProperties": {
                "type": "string",
                "description": "Any additional string field"
              }
            }
        """.trimIndent()

        // Hover over "customValue" (line 0, column 13)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 13)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Any additional string field"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_unionType() {
        val document = """
            value: 123
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "value": {
                  "type": ["string", "number"],
                  "description": "Can be either string or number"
                }
              }
            }
        """.trimIndent()

        // Hover over "123" (line 0, column 7)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 7)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Can be either string or number"))
        assertTrue(hoverInfo.contains("*Type:* `string | number`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_deeplyNestedArray() {
        val document = """
            matrix:
              -
                - 1
                - 2
              -
                - 3
                - 4
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "properties": {
                "matrix": {
                  "type": "array",
                  "items": {
                    "type": "array",
                    "items": {
                      "type": "number",
                      "description": "Matrix cell value"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        // Hover over "1" in nested array (line 2, column 7)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 2, 7)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("Matrix cell value"))
        assertTrue(hoverInfo.contains("*Type:* `number`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_invalidDocument() {
        val invalidDocument = """
            {invalid kson
        """.trimIndent()

        val schema = """
            {
              "type": "object"
            }
        """.trimIndent()

        // Should return null for invalid document
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(invalidDocument, schema, 0, 5)

        assertNull(hoverInfo)
    }

    @Test
    fun testGetSchemaInfoAtLocation_patternProperties() {
        val document = """
            field_1: value1
            field_2: value2
        """.trimIndent()

        val schema = """
            {
              "type": "object",
              "patternProperties": {
                "^field_": {
                  "type": "string",
                  "description": "A field matching the pattern"
                }
              }
            }
        """.trimIndent()

        // Hover over "value1" (line 0, column 9)
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 0, 9)

        assertNotNull(hoverInfo)
        assertTrue(hoverInfo.contains("A field matching the pattern"))
        assertTrue(hoverInfo.contains("*Type:* `string`"))
    }

    @Test
    fun testGetSchemaInfoAtLocation_refDefinition() {
        // Simplified test for $ref resolution
        val schema = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "items": {
                    "${'$'}ref": "#/${'$'}defs/Item"
                  }
                }
              },
              "${'$'}defs": {
                "Item": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "Item name from ref"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val document = """
            items:
              - name: foo
        """.trimIndent()

        // Hover over "foo" (line 1, column 11)
        // This should resolve the $ref to #/$defs/Item and show the name field schema
        val hoverInfo = KsonTooling.getSchemaInfoAtLocation(document, schema, 1, 11)

        assertNotNull(hoverInfo, "Expected hover info for name field. Got null")
        assertTrue(hoverInfo.contains("Item name from ref"), "Expected description from resolved ref. Got: $hoverInfo")
        assertTrue(hoverInfo.contains("*Type:* `string`"), "Expected type from resolved ref. Got: $hoverInfo")
    }
}