package org.kson

import kotlin.test.*

class DiagnosticTest {

    // ---- Parse errors (no schema) ----

    @Test
    fun testEmptyDocumentReportsError() {
        val diagnostics = KsonTooling.validateDocument("")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, diagnostics[0].severity)
    }

    @Test
    fun testValidDocumentNoDiagnostics() {
        val diagnostics = KsonTooling.validateDocument("key: \"value\"")
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testValidObjectNoDiagnostics() {
        val diagnostics = KsonTooling.validateDocument("{ \"name\": \"test\", \"age\": 30 }")
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testValidArrayNoDiagnostics() {
        val diagnostics = KsonTooling.validateDocument("[1, 2, 3]")
        assertEquals(0, diagnostics.size)
    }

    @Test
    fun testExtraTokensAfterValue() {
        val diagnostics = KsonTooling.validateDocument("key: \"value\" extraValue")
        assertEquals(1, diagnostics.size)
        assertEquals(DiagnosticSeverity.COREPARSE_ERROR, diagnostics[0].severity)
    }

    @Test
    fun testUnclosedBrace() {
        val diagnostics = KsonTooling.validateDocument("{ \"name\": \"test\"")
        assertTrue(diagnostics.isNotEmpty(), "Should have at least one diagnostic for unclosed brace")
        assertTrue(diagnostics.any { it.severity == DiagnosticSeverity.COREPARSE_ERROR })
    }

    @Test
    fun testErrorsAndWarnings() {
        val content = listOf(
            "- {list_item: false false}",
            "    - deceptive_indent_list_item"
        ).joinToString("\n")
        val diagnostics = KsonTooling.validateDocument(content)
        assertEquals(2, diagnostics.size)
        assertEquals(DiagnosticSeverity.COREPARSE_ERROR, diagnostics[0].severity)
        assertEquals(DiagnosticSeverity.WARNING, diagnostics[1].severity)
    }

    @Test
    fun testDiagnosticsHaveRangeInformation() {
        val diagnostics = KsonTooling.validateDocument("")
        assertTrue(diagnostics.isNotEmpty())
        val range = diagnostics[0].range
        assertTrue(range.startLine >= 0)
        assertTrue(range.startColumn >= 0)
    }

    @Test
    fun testDiagnosticsHaveMessageText() {
        val diagnostics = KsonTooling.validateDocument("")
        assertTrue(diagnostics.isNotEmpty())
        assertTrue(diagnostics[0].message.isNotEmpty(), "Diagnostic message should not be empty")
    }

    // ---- Schema validation ----

    @Test
    fun testSchemaTypeMismatch() {
        val schema = """
            {
                type: object
                properties: {
                    age: { type: number }
                }
            }
        """.trimIndent()
        val diagnostics = KsonTooling.validateDocument("{ age: \"not a number\" }", schema)
        assertTrue(diagnostics.isNotEmpty(), "Should report type mismatch")
    }

    @Test
    fun testSchemaMissingRequiredProperty() {
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                }
                required: ["name"]
            }
        """.trimIndent()
        val diagnostics = KsonTooling.validateDocument("{ age: 30 }", schema)
        assertTrue(diagnostics.isNotEmpty(), "Should report missing required property")
    }

    @Test
    fun testValidDocumentMatchingSchema() {
        val schema = """
            {
                type: object
                properties: {
                    name: { type: string }
                    age: { type: number }
                }
            }
        """.trimIndent()
        val diagnostics = KsonTooling.validateDocument("{ name: \"Alice\", age: 30 }", schema)
        assertEquals(0, diagnostics.size)
    }

    // ---- Schema parse failure ----

    @Test
    fun testInvalidSchemaStillReturnsParseErrors() {
        // Invalid schema content that can't be parsed
        val invalidSchema = "{ this is not valid : : : }}}"
        // Document with a parse error
        val diagnostics = KsonTooling.validateDocument("key: \"value\" extra", invalidSchema)
        // Should still return at least the document parse errors, not crash
        assertTrue(diagnostics.isNotEmpty(), "Should return document parse errors even when schema is invalid")
    }

    @Test
    fun testValidDocumentWithBrokenSchemaReturnsNoDiagnostics() {
        val invalidSchema = "{ broken schema {{{{"
        val diagnostics = KsonTooling.validateDocument("key: \"value\"", invalidSchema)
        // Valid document + broken schema: should get 0 diagnostics since
        // document is valid and schema parse failure returns no schema messages
        assertEquals(0, diagnostics.size)
    }

    // ---- Document without schema ----

    @Test
    fun testNoSchemaReturnsOnlyParseErrors() {
        val diagnostics = KsonTooling.validateDocument("key: \"value\" extra")
        assertTrue(diagnostics.isNotEmpty())
    }

    @Test
    fun testNullSchemaReturnsOnlyParseErrors() {
        val diagnostics = KsonTooling.validateDocument("key: \"value\" extra", null)
        assertTrue(diagnostics.isNotEmpty())
    }
}
