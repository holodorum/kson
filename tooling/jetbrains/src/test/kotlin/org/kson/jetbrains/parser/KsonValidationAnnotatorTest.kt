package org.kson.jetbrains.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.DiagnosticSeverity
import org.kson.parser.messages.MessageType

/**
 * Tests for [KsonValidationAnnotator] that verify it correctly flows validation errors
 * from KsonCore to the IDE annotation system.
 */
class KsonValidationAnnotatorTest : BasePlatformTestCase() {

    companion object {
        private val REQUIRED_NAME_SCHEMA = """
            type: object
            properties:
              name:
                type: string
                .
              .
            required:
              - name
        """.trimIndent()
    }

    fun testValidKsonHasNoErrors() {
        val source = "key: \"value\""
        val file = myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        myFixture.checkHighlighting(false, false, false)

        // If we get here without exceptions, the annotator ran successfully
        assertNotNull("File should be created", file)
    }

    fun testInvalidKsonHasErrors() {
        // Use a simple syntax error that KsonCore will definitely catch
        val source = "\"unclosed string"
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile

        // This should find syntax errors via the KsonValidationAnnotator
        val highlights = myFixture.doHighlighting()

        assertTrue("Invalid KSON should have at least one error highlight", highlights.isNotEmpty())
        assertTrue(
            "Should have error-level highlights",
            highlights.any { it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal })
    }

    fun testBlankFileHasNoErrors() {
        // Blank files should not error in IDE context (unlike CLI parsing)
        val source = ""
        val file = myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        myFixture.checkHighlighting(false, false, false)

        // If we get here without exceptions, the annotator handled blank files correctly
        assertNotNull("File should be created even when blank", file)
    }

    fun testAnnotatorDirectly() {
        // Test the annotator directly to ensure it handles null gracefully
        val annotator = KsonValidationAnnotator()

        // Test null file text
        val result = annotator.doAnnotate(null)
        assertEquals("Null file text should return empty list", emptyList<Any>(), result)

        // Test valid KSON
        val validResult = annotator.doAnnotate(ValidationInfo("key: \"value\""))
        assertNotNull("Valid KSON should return a result", validResult)

        // Test invalid KSON  
        val invalidResult = annotator.doAnnotate(ValidationInfo("\"unclosed string"))
        assertNotNull("Invalid KSON should return messages", invalidResult)
        assertTrue("Invalid KSON should have error messages", invalidResult.isNotEmpty())
    }

    fun testCoreParseMessagesAreFiltered() {
        val source = "3.0.9"
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile

        // This should not show core parse messages as errors
        val highlights = myFixture.doHighlighting()
        assertEquals(
            "Should only have one error and not duplicate error in parsing and annotating.",
            highlights.size, 1
        )
    }

    fun testWarningsAreMarkedAsWarnings() {
        // Test that warnings like ignored end-dots are properly marked
        val source = """
            - list_item
                - deceptive_indent_list_item
        """.trimIndent()
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        
        val highlights = myFixture.doHighlighting()
        
        assertTrue("Should have at least one highlight for the warning", highlights.isNotEmpty())
        
        // Check that we have a warning-level highlight
        val listDashWarning = highlights.filter {
            it.severity.myVal == com.intellij.lang.annotation.HighlightSeverity.WARNING.myVal 
        }.find {
            val listDashMessage = MessageType.DASH_LIST_ITEMS_MISALIGNED.create().toString()
            it.description == listDashMessage
        }

        assertNotNull("Should have a warning about ignored end-dot", listDashWarning)
        
        // Ensure there are no errors for this valid (but warned) syntax
        val errorHighlights = highlights.filter { 
            it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal 
        }
        assertTrue("Should not have any errors for valid syntax with warnings", errorHighlights.isEmpty())
    }
    
    fun testSchemaValidationErrorsAppear() {
        // Create a schema that requires "name" to be a string
        myFixture.addFileToProject("test.schema.kson", REQUIRED_NAME_SCHEMA)

        // Create a document that references the schema but violates it (missing "name")
        myFixture.configureByText(KsonFileType, """
            '${'$'}schema': './test.schema.kson'
            age: 30
        """.trimIndent()) as KsonPsiFile

        val highlights = myFixture.doHighlighting()

        val requiredMessage = MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING.create("name").toString()
        val schemaErrors = highlights.filter { it.description == requiredMessage }
        assertTrue(
            "Should have schema validation error about missing required property",
            schemaErrors.isNotEmpty()
        )
    }

    fun testSchemaValidationDirectly() {
        val annotator = KsonValidationAnnotator()

        val requiredMessage = MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING.create("name").toString()

        // Valid document against schema — no schema errors
        val validResult = annotator.doAnnotate(ValidationInfo("name: \"Alice\"", REQUIRED_NAME_SCHEMA))
        val validSchemaErrors = validResult.filter {
            it.severity != DiagnosticSeverity.COREPARSE_ERROR && it.message == requiredMessage
        }
        assertTrue("Valid document should have no schema errors", validSchemaErrors.isEmpty())

        // Invalid document against schema — missing required "name"
        val invalidResult = annotator.doAnnotate(ValidationInfo("age: 30", REQUIRED_NAME_SCHEMA))
        val invalidSchemaErrors = invalidResult.filter {
            it.severity != DiagnosticSeverity.COREPARSE_ERROR && it.message == requiredMessage
        }
        assertTrue("Invalid document should have schema errors", invalidSchemaErrors.isNotEmpty())
    }

    fun testMultipleWarningsAndErrors() {
        // Test a case with both warnings and errors
        val source = """
            - {key:}
                - deceptive_indent_list_item
        """.trimIndent()
        myFixture.configureByText(KsonFileType, source) as KsonPsiFile
        
        val highlights = myFixture.doHighlighting()
        
        // Should have both warnings and errors
        val warningHighlights = highlights.filter { 
            it.severity.myVal == com.intellij.lang.annotation.HighlightSeverity.WARNING.myVal 
        }
        val errorHighlights = highlights.filter { 
            it.severity.myVal >= com.intellij.lang.annotation.HighlightSeverity.ERROR.myVal 
        }
        
        assertTrue("Should have at least one warning", warningHighlights.isNotEmpty())
        assertTrue("Should have at least one error", errorHighlights.isNotEmpty())
    }
}
