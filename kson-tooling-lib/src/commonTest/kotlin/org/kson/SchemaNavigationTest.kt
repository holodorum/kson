package org.kson

import org.kson.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SchemaNavigationTest {

    /**
     * Helper to parse KSON text to a KsonValue
     */
    private fun parseKson(source: String): KsonValue {
        val result = KsonCore.parseToAst(source)
        return result.ksonValue ?: run {
            val errorMessages = result.messages.joinToString("\n") { "${it.location}: ${it.message}" }
            error("Failed to parse KSON:\n$errorMessages\nSource:\n$source")
        }
    }

    /**
     * Helper to navigate schema and get the result value
     */
    private fun navigateSchema(schema: KsonValue, path: List<String>): KsonValue? {
        val resolved = KsonTooling.navigateSchemaByDocumentPath(schema, path)
        return resolved?.resolvedValue
    }

    @Test
    fun testNavigateEmptyPath() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    name: { type: "string" }
                }
            }
        """)

        val result = KsonTooling.navigateSchemaByDocumentPath(schema, emptyList())
        assertNotNull(result)
        assertEquals(schema, result.resolvedValue)
    }

    @Test
    fun testNavigateSimpleObjectProperty() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    name: {
                        type: "string"
                        description: "The name property"
                    }
                }
            }
        """)

        val result = navigateSchema(schema, listOf("name"))
        assertNotNull(result)

        // Verify we got the correct schema node
        val nameSchema = result as KsonObject
        assertEquals("string", (nameSchema.propertyLookup["type"] as? KsonString)?.value)
        assertEquals("The name property", (nameSchema.propertyLookup["description"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateNestedObjectProperties() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    user: {
                        type: "object"
                        properties: {
                            name: {
                                type: "string"
                                description: "User name"
                            }
                        }
                    }
                }
            }
        """)

        val result = navigateSchema(schema, listOf("user", "name"))
        assertNotNull(result)

        val nameSchema = result as KsonObject
        assertEquals("string", (nameSchema.propertyLookup["type"] as? KsonString)?.value)
        assertEquals("User name", (nameSchema.propertyLookup["description"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateArrayItems() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    users: {
                        type: "array"
                        items: {
                            type: "object"
                            properties: {
                                name: { type: "string" }
                            }
                        }
                    }
                }
            }
        """)

        // Navigate to users[0] - should give us the items schema
        val itemSchema = navigateSchema(schema, listOf("users", "0"))
        assertNotNull(itemSchema)
        assertEquals("object", ((itemSchema as KsonObject).propertyLookup["type"] as? KsonString)?.value)

        // Navigate deeper: users[0].name
        val nameSchema = navigateSchema(schema, listOf("users", "0", "name"))
        assertNotNull(nameSchema)
        assertEquals("string", ((nameSchema as KsonObject).propertyLookup["type"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateArrayItemsAnyIndex() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    tags: {
                        type: "array"
                        items: { type: "string" }
                    }
                }
            }
        """)

        // All indices should return the same items schema
        val item0 = navigateSchema(schema, listOf("tags", "0"))
        val item5 = navigateSchema(schema, listOf("tags", "5"))
        val item99 = navigateSchema(schema, listOf("tags", "99"))

        assertNotNull(item0)
        assertNotNull(item5)
        assertNotNull(item99)

        // They should all be the same schema (type: string)
        assertEquals("string", ((item0 as KsonObject).propertyLookup["type"] as? KsonString)?.value)
        assertEquals("string", ((item5 as KsonObject).propertyLookup["type"] as? KsonString)?.value)
        assertEquals("string", ((item99 as KsonObject).propertyLookup["type"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateAdditionalProperties() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    knownProp: { type: "string" }
                }
                additionalProperties: {
                    type: "number"
                    description: "Any other property is a number"
                }
            }
        """)

        // Known property should use the specific schema
        val knownProp = navigateSchema(schema, listOf("knownProp"))
        assertNotNull(knownProp)
        assertEquals("string", ((knownProp as KsonObject).propertyLookup["type"] as? KsonString)?.value)

        // Unknown property should use additionalProperties
        val unknownProp = navigateSchema(schema, listOf("unknownProp"))
        assertNotNull(unknownProp)
        assertEquals("number", ((unknownProp as KsonObject).propertyLookup["type"] as? KsonString)?.value)
        assertEquals("Any other property is a number", (unknownProp.propertyLookup["description"] as? KsonString)?.value)
    }

    @Test
    fun testNavigatePatternProperties() {
        val schema = parseKson("""
            {
                type: "object"
                patternProperties: {
                    "^age_.*": {
                        type: "integer"
                        description: "Age fields"
                    }
                    "^name_.*": {
                        type: "string"
                        description: "Name fields"
                    }
                }
            }
        """)

        // Property matching age_ pattern
        val ageProp = navigateSchema(schema, listOf("age_child"))
        assertNotNull(ageProp)
        assertEquals("integer", ((ageProp as KsonObject).propertyLookup["type"] as? KsonString)?.value)
        assertEquals("Age fields", (ageProp.propertyLookup["description"] as? KsonString)?.value)

        // Property matching name_ pattern
        val nameProp = navigateSchema(schema, listOf("name_first"))
        assertNotNull(nameProp)
        assertEquals("string", ((nameProp as KsonObject).propertyLookup["type"] as? KsonString)?.value)
        assertEquals("Name fields", (nameProp.propertyLookup["description"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateNonExistentProperty() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    name: { type: "string" }
                }
                additionalProperties: false
            }
        """)

        // Non-existent property with additionalProperties: false
        val result = navigateSchema(schema, listOf("nonexistent"))
        // Should return the additionalProperties value (false as KsonBoolean)
        assertNotNull(result)
        assertEquals(false, (result as? KsonBoolean)?.value)
    }

    @Test
    fun testNavigateInvalidPath() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    name: { type: "string" }
                }
            }
        """)

        // Try to navigate deeper into a primitive type
        val result = navigateSchema(schema, listOf("name", "invalid", "path"))
        assertNull(result)
    }

    @Test
    fun testNavigateComplexNestedPath() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    company: {
                        type: "object"
                        properties: {
                            departments: {
                                type: "array"
                                items: {
                                    type: "object"
                                    properties: {
                                        employees: {
                                            type: "array"
                                            items: {
                                                type: "object"
                                                properties: {
                                                    name: {
                                                        type: "string"
                                                        description: "Employee name"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """)

        // Navigate: company.departments[0].employees[0].name
        val path = listOf("company", "departments", "0", "employees", "0", "name")
        val result = navigateSchema(schema, path)
        assertNotNull(result)

        val nameSchema = result as KsonObject
        assertEquals("string", (nameSchema.propertyLookup["type"] as? KsonString)?.value)
        assertEquals("Employee name", (nameSchema.propertyLookup["description"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateWithSchemaId() {
        val schema = parseKson("""
            {
                '${'$'}id': "https://example.com/schema"
                type: "object"
                properties: {
                    name: {
                        '${'$'}id': "name-schema"
                        type: "string"
                    }
                }
            }
        """)

        val result = KsonTooling.navigateSchemaByDocumentPath(schema, listOf("name"), "")
        assertNotNull(result)

        // The base URI should be updated based on the $id
        // (This tests the URI tracking functionality)
        assertEquals("string", ((result.resolvedValue as KsonObject).propertyLookup["type"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateSchemaWithNoProperties() {
        val schema = parseKson("""
            {
                type: "object"
            }
        """)

        // Try to navigate to a property when none are defined
        val result = navigateSchema(schema, listOf("anyProp"))
        // Should return null since there's no properties or additionalProperties
        assertNull(result)
    }

    @Test
    fun testNavigateAdditionalItemsForArray() {
        val schema = parseKson("""
            {
                type: "object"
                properties: {
                    tuple: {
                        type: "array"
                        additionalItems: {
                            type: "boolean"
                            description: "Extra items are booleans"
                        }
                    }
                }
            }
        """)

        // Navigate to tuple array items - should use additionalItems
        val result = navigateSchema(schema, listOf("tuple", "0"))
        assertNotNull(result)
        assertEquals("boolean", ((result as KsonObject).propertyLookup["type"] as? KsonString)?.value)
    }

    @Test
    fun testNavigateWithInvalidRegexInPatternProperties() {
        // Test that invalid regex patterns are handled gracefully
        val schema = parseKson("""
            {
                type: "object"
                patternProperties: {
                    "^[invalid(regex": { type: "string" }
                    "^valid_.*": { type: "number" }
                }
                additionalProperties: { type: "boolean" }
            }
        """)

        // Should skip the invalid pattern and use additionalProperties
        val result = navigateSchema(schema, listOf("someProp"))
        assertNotNull(result)
        assertEquals("boolean", ((result as KsonObject).propertyLookup["type"] as? KsonString)?.value)

        // Valid pattern should still work
        val validResult = navigateSchema(schema, listOf("valid_prop"))
        assertNotNull(validResult)
        assertEquals("number", ((validResult as KsonObject).propertyLookup["type"] as? KsonString)?.value)
    }
}
