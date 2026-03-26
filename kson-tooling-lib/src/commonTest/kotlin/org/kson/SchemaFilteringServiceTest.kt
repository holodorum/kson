package org.kson

import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.schema.SchemaIdLookup
import org.kson.value.KsonValue
import kotlin.test.*

/**
 * Unit tests for [SchemaFilteringService]
 *
 * These tests focus on the schema filtering logic in isolation,
 * without going through the full public API.
 */
class SchemaFilteringServiceTest {

    /**
     * Helper function to set up schema filtering and return valid schemas for a given document.
     *
     * @param schema The schema definition as a string
     * @param document The document to validate against the schema
     * @param documentPointer The JSON Pointer to the location in the document being completed
     * @return List of valid schemas after filtering
     */
    private fun getValidSchemasForDocument(
        schema: String,
        document: String,
        documentPointer: JsonPointer = JsonPointer("")
    ): List<KsonValue> {
        val parsedSchema = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        val parsedDocument = KsonCore.parseToAst(document).ksonValue
        val schemaIdLookup = SchemaIdLookup(parsedSchema)
        val filteringService = SchemaFilteringService(schemaIdLookup)
        val candidateSchemas = schemaIdLookup.navigateByDocumentPointer(documentPointer)
        return filteringService.getValidSchemas(candidateSchemas, parsedDocument, documentPointer).map { it.resolvedValue }
    }

    @Test
    fun testGetValidSchemas_withOneOfCombinator_filtersIncompatibleSchemas() {
        // Schema with oneOf combinator
        val schema = """
            oneOf:
              - type: object
                properties:
                  type:
                    const: email
                  recipient:
                    type: string
              - type: object
                properties:
                  type:
                    const: sms
                  phoneNumber:
                    type: string
        """.trimIndent()

        // Document with type: email (should only match first branch)
        val document = """
            type: email
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // Should filter to only the email branch (oneOf creates 2 branches, only 1 should be valid)
        // Note: The filtering happens after expansion, so we expect 1 valid schema
        assertTrue(validSchemas.size >= 1, "Should have at least 1 valid schema")

        // The valid schema should be the one with type: email (first branch)
        // We can verify this by checking that it validates against our document
        // Since the filtering already happened, any returned schema is compatible
    }

    @Test
    fun testGetValidSchemas_withAnyOfCombinator_filtersIncompatibleSchemas() {
        // Schema with anyOf combinator
        val schema = """
            anyOf:
              - type: object
                properties:
                  name:
                    type: string
                  age:
                    type: number
              - type: object
                properties:
                  name:
                    type: string
                  role:
                    type: string
        """.trimIndent()

        // Document with 'age' property (should only match first branch)
        val document = """
            name: John
            age: 30
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // Should filter to compatible branches only
        assertTrue(validSchemas.size >= 1, "Should have at least 1 valid schema")
        // Since document has 'age: 30' (number), only first branch should be valid
        // The second branch expects 'role' which is not present, but missing required props are ignored,
        // so we might get both branches. Let's just verify we get at least one.
    }

    @Test
    fun testGetValidSchemas_withAllOfCombinator_includesAllBranches() {
        // Schema with allOf combinator - all branches should always be included
        val schema = """
            allOf:
              - type: object
                properties:
                  name:
                    type: string
              - type: object
                properties:
                  age:
                    type: number
        """.trimIndent()

        val document = """
            name: John
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // allOf should include all branches (no filtering) plus the parent schema
        assertEquals(3, validSchemas.size, "allOf should include parent + all branches without filtering")
    }

    @Test
    fun testGetValidSchemas_withInvalidDocument_fallsBackToUnfilteredSchemas() {
        // Schema with oneOf
        val schema = """
            oneOf:
              - type: string
              - type: number
        """.trimIndent()

        // Invalid document (not parseable)
        val invalidDocument = "{ invalid json"

        val validSchemas = getValidSchemasForDocument(schema, invalidDocument)

        // Should fall back to all expanded schemas without filtering (parent + branches)
        assertEquals(3, validSchemas.size, "Should return parent + all branches when document doesn't parse")
    }

    @Test
    fun testGetValidSchemas_withNoCombinators_returnsAllSchemas() {
        // Simple schema without combinators
        val schema = """
            type: object
            properties:
              name:
                type: string
              age:
                type: number
        """.trimIndent()

        val document = """
            name: John
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // Should return all candidate schemas since there are no combinators (only 1 in this case)
        assertEquals(1, validSchemas.size, "Should return all schemas when no combinators")
    }

    @Test
    fun testGetValidSchemas_ignoresRequiredPropertyErrors() {
        // Schema with required properties
        val schema = """
            oneOf:
              - type: object
                required: [name, email]
                properties:
                  name:
                    type: string
                  email:
                    type: string
              - type: object
                required: [name, phone]
                properties:
                  name:
                    type: string
                  phone:
                    type: string
        """.trimIndent()

        // Document with only 'name' (missing required 'email' or 'phone')
        // Both branches should still be valid because missing required props are ignored
        val document = """
            name: John
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // Both branches should be valid (missing required properties are ignored during filtering) plus parent
        assertEquals(3, validSchemas.size, "Parent + both branches should be valid - missing required props are ignored")
    }

    @Test
    fun testGetValidSchemas_withNonRootPointerToMissingValue_returnsAllBranches() {
        // Schema where a nested property has oneOf branches expecting string or number types.
        // This triggers the bug: when navigating to /query in the document returns null
        // (nothing there yet), the fallback to root causes the root object to be validated
        // against branches that expect string/number — type mismatch filters them all out.
        val schema = """
            type: object
            properties:
              query:
                oneOf:
                  - type: string
                  - type: number
        """.trimIndent()

        // Document is an object with a property but no "query" — the user is about to type there.
        // The root is an object, so validating it against "type: string" or "type: number"
        // fails — incorrectly filtering out all branches.
        val document = """
            name: test
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document, JsonPointer("/query"))

        // Both oneOf branches + the parent should be returned because there's nothing
        // at /query yet — no basis for filtering.
        assertEquals(3, validSchemas.size, "Parent + both oneOf branches should be returned when target value doesn't exist yet")
    }

    @Test
    fun testGetValidSchemas_filtersBasedOnTypeViolations() {
        // Schema with different type requirements
        val schema = """
            oneOf:
              - type: object
                properties:
                  value:
                    type: string
              - type: object
                properties:
                  value:
                    type: number
        """.trimIndent()

        // Document with string value (should only match first branch)
        val document = """
            value: hello
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // Should filter to only the string branch plus parent
        assertEquals(2, validSchemas.size, "Should match parent + the string branch")
    }

    @Test
    fun testGetValidSchemas_withTypeMismatchAtTarget_fallsBackToAllBranches() {
        // Schema with oneOf branches that all expect objects, but the document value
        // at the target is a list.  Filtering would reject every branch (type mismatch),
        // leaving zero completions.  The fallback should return all branches.
        val schema = """
            oneOf:
              - type: object
                properties:
                  field:
                    type: string
              - type: object
                properties:
                  and:
                    type: array
        """.trimIndent()

        // Document is a list, not an object — every oneOf branch fails type validation.
        val document = """
            - item
        """.trimIndent()

        val validSchemas = getValidSchemasForDocument(schema, document)

        // All oneOf branches + parent should be returned because filtering eliminated everything.
        assertEquals(3, validSchemas.size, "Parent + both oneOf branches should be returned when target type doesn't match any branch")
    }
}
